package scaladex.server.service

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.zip.ZipInputStream

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Using
import scala.util.control.NonFatal

import scaladex.core.model.Artifact
import scaladex.core.service.MavenCentralClient
import scaladex.server.service.ScaladocService.*

import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.blemale.scaffeine.AsyncLoadingCache
import com.github.blemale.scaffeine.Scaffeine
import com.typesafe.scalalogging.LazyLogging

/** Serves a project's scaladoc by lazily downloading and unpacking its `-javadoc.jar` from Maven Central on first
  * request, then serving the unpacked files from disk on every subsequent request. Unpacked artifacts are tracked in a
  * `maxCacheBytes`-bounded cache, keyed by their size on disk: once the total exceeds that budget, the least recently
  * used ones are evicted and their files deleted, so disk usage stays bounded under sustained traffic.
  */
class ScaladocService(
    cacheDir: Path,
    mavenCentralClient: MavenCentralClient,
    maxCacheBytes: Long,
    maxUnpackedBytes: Long
)(using ExecutionContext)
    extends LazyLogging:
  if Files.notExists(cacheDir) then Files.createDirectories(cacheDir)

  private val cache: AsyncLoadingCache[Artifact.Reference, Entry] =
    Scaffeine()
      .maximumWeight(maxCacheBytes)
      .weigher((_, entry: Entry) => entry.sizeOnDisk.toInt)
      .removalListener((ref: Artifact.Reference, _, cause) =>
        if cause != RemovalCause.REPLACED then deleteRecursively(localDir(ref))
      )
      .buildAsyncFuture(load)

  def localDir(ref: Artifact.Reference): Path =
    cacheDir
      .resolve(ref.groupId.mavenUrl)
      .resolve(ref.artifactId.value)
      .resolve(ref.version.value)

  /** @return
    *   true if the scaladoc for this artifact is available on disk (either already was, or was just downloaded and
    *   unpacked), false if Maven Central has no javadoc jar for it.
    */
  def ensureAvailable(ref: Artifact.Reference): Future[Boolean] = cache.get(ref).map {
    case Entry.Available(_) => true
    case Entry.Unavailable => false
  }

  private def isUnpacked(ref: Artifact.Reference): Boolean =
    Files.isRegularFile(localDir(ref).resolve("index.html"))

  private def load(ref: Artifact.Reference): Future[Entry] =
    if isUnpacked(ref) then Future.successful(Entry.Available(sizeOnDisk(localDir(ref))))
    else
      mavenCentralClient.getJavadocJar(ref).map {
        case None => Entry.Unavailable
        case Some(bytes) =>
          try
            unpack(bytes, localDir(ref))
            Entry.Available(sizeOnDisk(localDir(ref)))
          catch
            case NonFatal(exception) =>
              logger.warn(s"Failed to unpack javadoc jar of $ref", exception)
              Entry.Unavailable
      }

  /** Unpacks into a sibling temp directory, then atomically moves it into place - so a concurrent request never sees a
    * partially-unpacked directory.
    */
  private def unpack(bytes: Array[Byte], destination: Path): Unit =
    val staging = Files.createTempDirectory(cacheDir, "unpacking-")
    try
      Using.resource(new ZipInputStream(new ByteArrayInputStream(bytes))) { zip =>
        val boundedZip = BoundedInputStream(zip, maxUnpackedBytes)
        Iterator
          .continually(zip.getNextEntry)
          .takeWhile(_ != null)
          .foreach { entry =>
            // guard against zip-slip: reject any entry that would resolve outside `staging`
            val entryPath = staging.resolve(entry.getName).normalize()
            if !entryPath.startsWith(staging) then
              throw SecurityException(s"Zip entry escapes destination directory: ${entry.getName}")
            if entry.isDirectory then Files.createDirectories(entryPath)
            else
              Files.createDirectories(entryPath.getParent)
              Files.copy(boundedZip, entryPath, StandardCopyOption.REPLACE_EXISTING)
          }
      }
      Files.createDirectories(destination.getParent)
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
    finally deleteRecursively(staging)
    end try
  end unpack

  private def sizeOnDisk(dir: Path): Long =
    Using.resource(Files.walk(dir)) { stream => stream.filter(Files.isRegularFile(_)).mapToLong(Files.size(_)).sum() }

  private def deleteRecursively(dir: Path): Unit = if Files.exists(dir) then
    Using.resource(Files.walk(dir)) { stream =>
      stream.sorted(Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))
    }
end ScaladocService

object ScaladocService:
  private enum Entry(val sizeOnDisk: Long):
    case Unavailable extends Entry(0L)
    case Available(size: Long) extends Entry(size)

  /** Rejects reads past `maxBytes`, tracked cumulatively across every entry read through this instance. Only overrides
    * the bulk-read method, since that's the one `Files.copy` actually calls.
    */
  private final class BoundedInputStream(in: InputStream, maxBytes: Long) extends FilterInputStream(in):
    private var remaining = maxBytes

    override def read(b: Array[Byte], off: Int, len: Int): Int =
      val n = super.read(b, off, len)
      if n > 0 then
        remaining -= n
        if remaining < 0 then
          throw new SecurityException(s"Zip content exceeds maximum unpacked size of $maxBytes bytes")
      n
  end BoundedInputStream

end ScaladocService
