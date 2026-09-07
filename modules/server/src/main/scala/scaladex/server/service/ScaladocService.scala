package scaladex.server.service

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal

import scaladex.core.model.Artifact
import scaladex.core.service.MavenCentralClient

import com.typesafe.scalalogging.LazyLogging

/** Serves a project's scaladoc by lazily downloading and unpacking its `-javadoc.jar` from Maven Central on first
  * request, then serving the unpacked files from disk on every subsequent request. There is deliberately no eviction:
  * this is meant to run on a single server with disk to spare, and we'd rather learn real usage before adding one.
  */
class ScaladocService(cacheDir: Path, mavenCentralClient: MavenCentralClient)(using ExecutionContext)
    extends LazyLogging:
  // Only dedupes concurrent requests for the same not-yet-cached artifact; cleared once resolved either way, so a
  // transient failure (Maven Central hiccup) doesn't get baked in - the next request just tries again.
  private val inFlight = TrieMap.empty[Artifact.Reference, Future[Boolean]]

  def localDir(ref: Artifact.Reference): Path =
    cacheDir
      .resolve(ref.groupId.value.replace('.', '/'))
      .resolve(ref.artifactId.value)
      .resolve(ref.version.value)

  /** @return
    *   true if the scaladoc for this artifact is available on disk (either already was, or was just downloaded and
    *   unpacked), false if Maven Central has no javadoc jar for it.
    */
  def ensureAvailable(ref: Artifact.Reference): Future[Boolean] =
    if isUnpacked(ref) then Future.successful(true)
    else
      inFlight.getOrElseUpdate(
        ref,
        download(ref).andThen { case _ => inFlight.remove(ref) }
      )

  private def isUnpacked(ref: Artifact.Reference): Boolean =
    Files.isRegularFile(localDir(ref).resolve("index.html"))

  private def download(ref: Artifact.Reference): Future[Boolean] =
    mavenCentralClient.getJavadocJar(ref).map {
      case None => false
      case Some(bytes) =>
        try
          unpack(bytes, localDir(ref))
          true
        catch
          case NonFatal(exception) =>
            logger.warn(s"Failed to unpack javadoc jar of $ref", exception)
            false
    }

  /** Unpacks into a sibling temp directory, then atomically moves it into place - so a concurrent request never sees
    * a partially-unpacked directory.
    */
  private def unpack(bytes: Array[Byte], destination: Path): Unit =
    val staging = Files.createTempDirectory(cacheDir, "unpacking-")
    try
      Using.resource(new ZipInputStream(new ByteArrayInputStream(bytes))) { zip =>
        Iterator
          .continually(zip.getNextEntry)
          .takeWhile(_ != null)
          .foreach { entry =>
            // guard against zip-slip: reject any entry that would resolve outside `staging`
            val entryPath = staging.resolve(entry.getName).normalize()
            if !entryPath.startsWith(staging) then
              throw new SecurityException(s"Zip entry escapes destination directory: ${entry.getName}")
            if entry.isDirectory then Files.createDirectories(entryPath)
            else
              Files.createDirectories(entryPath.getParent)
              Files.copy(zip, entryPath, StandardCopyOption.REPLACE_EXISTING)
          }
      }
      Files.createDirectories(destination.getParent)
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
    finally deleteRecursively(staging)
  end unpack

  // no-op once `unpack` has already moved `staging` away; only cleans up on failure
  private def deleteRecursively(dir: Path): Unit =
    if Files.exists(dir) then
      Using.resource(Files.walk(dir)) { stream =>
        stream.iterator.asScala.toSeq.reverse.foreach(Files.deleteIfExists)
      }
end ScaladocService

object ScaladocService:
  def apply(cacheDir: Path, mavenCentralClient: MavenCentralClient)(using ExecutionContext): ScaladocService =
    if Files.notExists(cacheDir) then Files.createDirectories(cacheDir)
    new ScaladocService(cacheDir, mavenCentralClient)
end ScaladocService
