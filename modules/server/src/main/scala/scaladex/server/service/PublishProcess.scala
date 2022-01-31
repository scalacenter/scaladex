package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Project
import scaladex.core.model.Sha1
import scaladex.core.model.UserState
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.service.Storage
import scaladex.core.service.WebDatabase
import scaladex.data.cleanup.GithubRepoExtractor
import scaladex.data.maven.ArtifactModel
import scaladex.data.maven.PomsReader
import scaladex.data.meta.ArtifactConverter
import scaladex.infra.CoursierResolver
import scaladex.infra.DataPaths

sealed trait PublishResult
object PublishResult {
  object InvalidPom extends PublishResult
  object NoGithubRepo extends PublishResult
  object Success extends PublishResult
  case class Forbidden(login: String, repo: Project.Reference) extends PublishResult
}

class PublishProcess(
    filesystem: Storage,
    githubExtractor: GithubRepoExtractor,
    converter: ArtifactConverter,
    database: WebDatabase,
    pomsReader: PomsReader
)(implicit ec: ExecutionContext)
    extends LazyLogging {
  def publishPom(
      path: String,
      data: String,
      creationDate: Instant,
      userState: UserState
  ): Future[PublishResult] =
    if (isPom(path)) {
      logger.info(s"Publishing POM $path")
      val sha1 = Sha1(data)
      val tempFile = filesystem.createTempFile(data, sha1, ".pom")
      val future =
        Future(pomsReader.loadOne(tempFile).get)
          .flatMap { case (pom, _) => publishPom(pom, data, sha1, creationDate, userState) }
          .recover { cause =>
            logger.error("Invalid POM", cause)
            PublishResult.InvalidPom
          }
      future.onComplete(_ => filesystem.deleteTempFile(tempFile))
      future
    } else {
      logger.warn(s"Received invalid pom from ${userState.info.login}: $path")
      Future.successful(PublishResult.InvalidPom)
    }

  private def publishPom(
      pom: ArtifactModel,
      data: String,
      sha1: String,
      creationDate: Instant,
      userState: UserState
  ): Future[PublishResult] = {
    val repository =
      if (userState.hasPublishingAuthority) LocalPomRepository.MavenCentral
      else LocalPomRepository.UserProvided
    val pomRef = s"${pom.groupId}:${pom.artifactId}:${pom.version}"
    githubExtractor.extract(pom) match {
      case None =>
        // TODO: save artifact with no github information
        Future.successful(PublishResult.NoGithubRepo)
      case Some(repo) =>
        if (userState.hasPublishingAuthority || userState.repos.contains(repo)) {
          converter.convert(pom, repo, creationDate) match {
            case Some((artifact, deps)) =>
              database
                .insertArtifact(artifact, deps, Instant.now)
                .map { _ =>
                  logger.info(s"Published $pomRef")
                  PublishResult.Success
                }
            case None =>
              logger.warn(s"Cannot convert $pomRef to valid Scala artifact.")
              Future.successful(PublishResult.InvalidPom)
          }
        } else {
          logger.warn(s"User ${userState.info.login} attempted to publish to $repo")
          Future.successful(PublishResult.Forbidden(userState.info.login, repo))
        }
    }
  }

  private def isPom(path: String): Boolean = path.matches(""".*\.pom""")
}

object PublishProcess {
  def apply(paths: DataPaths, filesystem: Storage, database: WebDatabase)(
      implicit ec: ExecutionContext
  ): PublishProcess = {
    val githubExtractor = new GithubRepoExtractor(paths)
    val converter = new ArtifactConverter(paths)
    val pomsReader = new PomsReader(new CoursierResolver)
    new PublishProcess(filesystem, githubExtractor, converter, database, pomsReader)
  }
}
