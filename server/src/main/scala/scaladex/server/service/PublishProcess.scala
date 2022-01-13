package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Sha1
import scaladex.core.model.UserState
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.service.LocalStorageApi
import scaladex.core.service.WebDatabase
import scaladex.data.cleanup.GithubRepoExtractor
import scaladex.data.maven.ArtifactModel
import scaladex.data.maven.PomsReader
import scaladex.data.meta.ArtifactConverter
import scaladex.infra.storage.DataPaths

class PublishProcess(
    filesystem: LocalStorageApi,
    githubExtractor: GithubRepoExtractor,
    converter: ArtifactConverter,
    database: WebDatabase
)(implicit ec: ExecutionContext)
    extends LazyLogging {
  def publishPom(
      path: String,
      data: String,
      creationDate: Instant,
      userState: UserState
  ): Future[(StatusCode, String)] = {
    val repository =
      if (userState.hasPublishingAuthority) LocalPomRepository.MavenCentral
      else LocalPomRepository.UserProvided
    if (isPom(path)) {
      logger.info(s"Publishing POM $path")
      val sha1 = Sha1(data)
      val tempFile = filesystem.createTempFile(data, sha1, ".pom")
      try PomsReader.loadOne(repository, tempFile) match {
        case Success(pom) =>
          publishPom(pom, data, sha1, repository, creationDate, userState)
        case Failure(e) =>
          logger.error("Invalid POM", e)
          Future.successful((BadRequest, "Invalid pom"))
      } finally filesystem.deleteTempFile(tempFile)
    } else Future.successful((BadRequest, "Not a POM"))
  }

  private def publishPom(
      pom: ArtifactModel,
      data: String,
      sha1: String,
      repository: LocalPomRepository,
      creationDate: Instant,
      userState: UserState
  ): Future[(StatusCode, String)] =
    githubExtractor.extract(pom) match {
      case None =>
        // TODO: save artifact with no github information
        Future.successful((NoContent, "No Github Repo"))
      case Some(repo) =>
        if (userState.hasPublishingAuthority || userState.repos.contains(repo)) {
          converter.convert(pom, repo, Some(creationDate)) match {
            case Some((artifact, deps)) =>
              logger.info(s"Saving ${pom.groupId}:${pom.artifactId}:${pom.version}")
              filesystem.savePom(data, sha1, repository)
              database
                .insertArtifact(artifact, deps, Instant.now)
                .map(_ => (Created, "Published artifact"))
            case None =>
              logger.warn(s"Cannot convert ${pom.groupId}:${pom.artifactId}:${pom.version} to valid Scala artifact.")
              Future.successful((NoContent, "Not valid pom"))
          }
        } else {
          logger.warn(s"User ${userState.info.login} attempted to publish to $repo")
          Future.successful((Forbidden, s"${userState.info.login} cannot publish to $repo"))
        }
    }

  private def isPom(path: String): Boolean = path.matches(""".*\.pom""")
}

object PublishProcess {
  def apply(paths: DataPaths, filesystem: LocalStorageApi, database: WebDatabase)(
      implicit ec: ExecutionContext
  ): PublishProcess = {
    val githubExtractor = new GithubRepoExtractor(paths)
    val converter = new ArtifactConverter(paths)
    new PublishProcess(filesystem, githubExtractor, converter, database)
  }
}
