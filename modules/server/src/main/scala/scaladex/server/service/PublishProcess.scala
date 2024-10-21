package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import scaladex.core.model.Artifact
import scaladex.core.model.Env
import scaladex.core.model.Project
import scaladex.core.model.Sha1
import scaladex.core.model.UserState
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.Storage
import scaladex.core.service.WebDatabase
import scaladex.data.cleanup.GithubRepoExtractor
import scaladex.data.maven.ArtifactModel
import scaladex.data.maven.PomsReader
import scaladex.infra.CoursierResolver
import scaladex.infra.DataPaths
import scaladex.infra.GithubClientImpl
import scaladex.server.service.ArtifactConverter

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
    artifactService: ArtifactService,
    pomsReader: PomsReader,
    env: Env
)(implicit system: ActorSystem)
    extends LazyLogging {
  import system.dispatcher

  def publishPom(
      path: String,
      data: String,
      creationDate: Instant,
      userState: Option[UserState]
  ): Future[PublishResult] = {
    logger.info(s"Publishing POM $path")
    Future(loadPom(data).get)
      .flatMap(publishPom(_, creationDate, userState))
      .recover { cause =>
        logger.error(s"Invalid POM $path", cause)
        PublishResult.InvalidPom
      }
  }

  private def publishPom(
      pom: ArtifactModel,
      creationDate: Instant,
      userState: Option[UserState]
  ): Future[PublishResult] = {
    val pomRef = s"${pom.groupId}:${pom.artifactId}:${pom.version}"
    githubExtractor.extract(pom) match {
      case None =>
        // TODO: save artifact with no github information
        Future.successful(PublishResult.NoGithubRepo)
      case Some(repo) =>
        // userState can be empty when the request of publish is done through the scheduler
        if (userState.forall(userState => userState.hasPublishingAuthority(env) || userState.repos.contains(repo))) {
          converter.convert(pom, repo, creationDate) match {
            case Some((artifact, deps)) =>
              for {
                isNewProject <- artifactService.insertArtifact(artifact, deps)
                _ <-
                  if (isNewProject && userState.nonEmpty) {
                    val githubUpdater = new GithubUpdater(database, new GithubClientImpl(userState.get.info.token))
                    githubUpdater.update(repo).map(_ => ())
                  } else Future.successful(())
              } yield {
                logger.info(s"Published $pomRef")
                PublishResult.Success
              }
            case None =>
              logger.warn(s"Cannot convert $pomRef to valid Scala artifact.")
              Future.successful(PublishResult.InvalidPom)
          }
        } else {
          logger.warn(s"User ${userState.get.info.login} attempted to publish to $repo")
          Future.successful(PublishResult.Forbidden(userState.get.info.login, repo))
        }
    }
  }

  def republishPom(
      repo: Project.Reference,
      ref: Artifact.Reference,
      data: String,
      creationDate: Instant
  ): Future[PublishResult] =
    Future(loadPom(data).get)
      .flatMap { pom =>
        converter.convert(pom, repo, creationDate).map(_._1) match {
          case Some(artifact) if artifact.reference == ref =>
            database.insertArtifact(artifact).map(_ => PublishResult.Success)
          case Some(artifact) =>
            logger.error(s"Unexpected ref ${artifact.reference}")
            Future.successful(PublishResult.InvalidPom)
          case None =>
            logger.warn(s"Cannot convert $ref to valid Scala artifact.")
            Future.successful(PublishResult.InvalidPom)
        }
      }
      .recover { cause =>
        logger.warn(s"Invalid POM $ref", cause)
        PublishResult.InvalidPom
      }

  private def loadPom(data: String): Try[ArtifactModel] = {
    val sha1 = Sha1(data)
    val tempFile = filesystem.createTempFile(data, sha1, ".pom")
    val res = pomsReader.loadOne(tempFile)
    filesystem.deleteTempFile(tempFile)
    res
  }
}

object PublishProcess {
  def apply(paths: DataPaths, filesystem: Storage, database: SchedulerDatabase, env: Env)(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem
  ): PublishProcess = {
    val githubExtractor = new GithubRepoExtractor(paths)
    val converter = new ArtifactConverter(paths)
    val pomsReader = new PomsReader(new CoursierResolver)
    val artifactService = new ArtifactService(database)
    new PublishProcess(filesystem, githubExtractor, converter, database, artifactService, pomsReader, env)
  }
}
