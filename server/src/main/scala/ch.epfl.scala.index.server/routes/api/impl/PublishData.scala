package ch.epfl.scala.index
package server
package routes
package api
package impl

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

import scala.util.control.NonFatal

import ch.epfl.scala.index.data.github
import ch.epfl.scala.index.model.misc.Sha1
import ch.epfl.scala.index.model.misc.UserState
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.LocalPomRepository
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

/**
 * Publish data model / Settings
 * @param path the file name send to scaladex
 * @param created the datime the release was published on the upstream repository
 * @param data the file content
 * @param credentials the credentials (username & password)
 * @param downloadInfo flag for downloading info
 * @param downloadContributors flag for downloading contributors
 * @param downloadReadme flag for downloading the readme file
 */
private[api] case class PublishData(
    path: String,
    created: Instant,
    data: String,
    credentials: github.Credentials,
    userState: UserState,
    downloadInfo: Boolean,
    downloadContributors: Boolean,
    downloadReadme: Boolean
) {
  private val log = LoggerFactory.getLogger(getClass)
  lazy val isPom: Boolean = path.matches(""".*\.pom""")
  lazy val hash: String = Sha1(data)
  lazy val tempPath: Path = tmpPath(hash)
  def savePath(paths: DataPaths): Path = pomPath(paths, hash)

  val datetimeCreated = new DateTime(created.toEpochMilli)

  /**
   * write the file content to given path
   * @param destination the given destination
   */
  private def write(destination: Path): Unit = {
    delete(destination)
    Files.write(destination, data.getBytes(StandardCharsets.UTF_8))
    ()
  }

  /**
   * delete a given file
   * @param file the file name to delete
   */
  private def delete(file: Path): Unit = {

    if (Files.exists(file)) Files.delete(file)
    ()
  }

  /**
   * write the temp file to /tmp/sha/sha.pom
   */
  def writeTemp(): Unit = write(tempPath)

  /**
   * write the pom file to /index/bintray/pom_sha1
   */
  def writePom(paths: DataPaths): Unit = write(savePath(paths))

  /**
   * delete the temp add file
   */
  def deleteTemp(): Unit = {
    delete(tempPath)
    val directory = tempPath.getParent
    try Files.delete(directory)
    catch {
      case NonFatal(error) =>
        log.error("Unable to delete temporary directory", error)
    }
  }

  /**
   * resolve the filename for a specific pom by sha1
   *
   * @param sha1 the sha1 hash of the file
   * @return
   */
  private def pomPath(paths: DataPaths, sha1: String): Path = {
    val repository =
      if (userState.isSonatype) LocalPomRepository.MavenCentral
      else LocalPomRepository.UserProvided

    paths.poms(repository).resolve(s"$sha1.pom")
  }

  /**
   * get the tmp save path for the pom file
   * @param sha1 the sha1 hash
   * @return
   */
  private def tmpPath(sha1: String): Path = {
    val tmpDir =
      Files.createTempDirectory(Paths.get(Server.config.tempDirPath), sha1)
    Files.createTempFile(tmpDir, "", "")
  }
}
