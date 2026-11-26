package scaladex.server.service

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.zip.ZipFile

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.Using
import scala.util.control.NonFatal

import scaladex.core.model.Artifact
import scaladex.core.service.MavenCentralClient
import scaladex.server.service.ScaladocService.*

import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.blemale.scaffeine.AsyncLoadingCache
import com.github.blemale.scaffeine.Scaffeine
import com.typesafe.scalalogging.LazyLogging

/** Serves a project's scaladoc by lazily downloading its `-javadoc.jar` from Maven Central on first request and storing
  * it on disk as-is. Files are then read straight out of the jar on each request - never unpacked - so each artifact
  * costs one compressed file on disk instead of thousands of small uncompressed ones.
  *
  * Stored jars are tracked in a `maxCacheBytes`-bounded cache, weighted by jar size: once the total exceeds that budget
  * the least recently used are evicted and their jars deleted. The cache only lives in memory, so `cacheDir` is wiped
  * on startup - otherwise jars stored by a previous run would sit on disk forever, uncounted.
  */
class ScaladocService(
    cacheDir: Path,
    mavenCentralClient: MavenCentralClient,
    maxCacheBytes: Long,
    maxJarBytes: Long
)(using ExecutionContext)
    extends LazyLogging:
  deleteRecursively(cacheDir)
  Files.createDirectories(cacheDir)

  private val cache: AsyncLoadingCache[Artifact.Reference, Entry] =
    Scaffeine()
      .maximumWeight(maxCacheBytes)
      .weigher((_, entry: Entry) => entry.weight)
      .removalListener((_: Artifact.Reference, entry: Entry, cause) =>
        entry.close(deleteJar = cause != RemovalCause.REPLACED)
      )
      .buildAsyncFuture(load)

  /** @return
    *   true if the scaladoc for this artifact is available (either already was, or was just downloaded), false if Maven
    *   Central has no usable javadoc jar for it.
    */
  def ensureAvailable(ref: Artifact.Reference): Future[Boolean] = cache.get(ref).map {
    case _: Entry.Available => true
    case Entry.Unavailable => false
  }

  /** Reads one file of the artifact's scaladoc. An empty path or a directory resolves to its `index.html`.
    *
    * @return
    *   the resolved file name and its content, or None if the scaladoc is unavailable or has no such file.
    */
  def getFile(ref: Artifact.Reference, path: String): Future[Option[(String, Array[Byte])]] = cache.get(ref).map {
    case Entry.Unavailable => None
    case entry: Entry.Available => entry.read(path)
  }

  private def jarPath(ref: Artifact.Reference): Path =
    cacheDir
      .resolve(ref.groupId.mavenUrl)
      .resolve(ref.artifactId.value)
      .resolve(s"${ref.version.value}.jar")

  private def load(ref: Artifact.Reference): Future[Entry] =
    mavenCentralClient.getJavadocJar(ref).map {
      case None => Entry.Unavailable
      case Some(bytes) if bytes.length > maxJarBytes =>
        logger.warn(s"Javadoc jar of $ref is ${bytes.length} bytes, over the limit of $maxJarBytes")
        Entry.Unavailable
      case Some(bytes) =>
        val destination = jarPath(ref)
        try
          store(bytes, destination)
          Entry.Available(destination, ZipFile(destination.toFile), bytes.length, maxJarBytes)
        catch
          case NonFatal(exception) =>
            logger.warn(s"Failed to store javadoc jar of $ref", exception)
            Files.deleteIfExists(destination)
            Entry.Unavailable
    }

  /** Writes to a temp file first, then atomically moves it into place, so a reader never sees a partial jar. */
  private def store(bytes: Array[Byte], destination: Path): Unit =
    Files.createDirectories(destination.getParent)
    val staging = Files.createTempFile(destination.getParent, "downloading-", ".jar")
    try
      Files.write(staging, bytes)
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    finally Files.deleteIfExists(staging)
end ScaladocService

object ScaladocService:
  // Caffeine never evicts zero-weight entries, so misses must weigh something to stay bounded.
  private val unavailableWeight: Int = 16 * 1024

  private enum Entry:
    case Unavailable
    case Available(jar: Path, zip: ZipFile, size: Int, maxEntryBytes: Long)

    def weight: Int = this match
      case Unavailable => unavailableWeight
      case Available(_, _, size, _) => size

    def read(path: String): Option[(String, Array[Byte])] = this match
      case Unavailable => None
      case Available(_, zip, _, maxEntryBytes) =>
        val name = path.stripPrefix("/")
        val candidates =
          if name.isEmpty then Seq("index.html")
          else if name.endsWith("/") then Seq(s"${name}index.html")
          else Seq(name, s"$name/index.html")
        candidates.iterator
          .map(zip.getEntry)
          .find(e => e != null && !e.isDirectory)
          .map { entry =>
            Using.resource(zip.getInputStream(entry)) { in =>
              // guard against zip bombs: a tiny compressed entry can claim to inflate to gigabytes
              val bytes = in.readNBytes(maxEntryBytes.toInt)
              if in.read() != -1 then
                throw SecurityException(s"Zip entry ${entry.getName} exceeds maximum size of $maxEntryBytes bytes")
              entry.getName -> bytes
            }
          }

    def close(deleteJar: Boolean): Unit = this match
      case Unavailable => ()
      case Available(jar, zip, _, _) =>
        Try(zip.close())
        if deleteJar then Files.deleteIfExists(jar)
  end Entry

  private def deleteRecursively(dir: Path): Unit = if Files.exists(dir) then
    Using.resource(Files.walk(dir)) { stream =>
      stream.sorted(Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))
    }
end ScaladocService
