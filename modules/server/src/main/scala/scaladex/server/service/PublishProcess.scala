package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorSystem
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Env
import scaladex.core.model.Project
import scaladex.core.model.Sha1
import scaladex.core.model.UserState
import scaladex.core.service.Storage
import scaladex.core.service.WebDatabase
import scaladex.core.util.ScalaExtensions._
import scaladex.data.cleanup.GithubRepoExtractor
import scaladex.data.maven.ArtifactModel
import scaladex.data.maven.PomsReader
import scaladex.data.meta.ArtifactConverter
import scaladex.infra.CoursierResolver
import scaladex.infra.DataPaths
import scaladex.infra.GithubClient

sealed trait PublishResult
object PublishResult {
  object InvalidPom extends PublishResult
  object NoGithubRepo extends PublishResult
  object Success extends PublishResult
  case class Forbidden(login: String, repo: Option[Project.Reference]) extends PublishResult
}

class PublishProcess(
    filesystem: Storage,
    githubExtractor: GithubRepoExtractor,
    converter: ArtifactConverter,
    database: WebDatabase,
    pomsReader: PomsReader,
    env: Env
)(implicit ec: ExecutionContext, system: ActorSystem)
    extends LazyLogging {
  def publishPom(
      path: String,
      data: String,
      creationDate: Instant,
      userState: Option[UserState]
  ): Future[PublishResult] = {
    logger.info(s"Publishing POM $path")
    val sha1 = Sha1(data)
    val tempFile = filesystem.createTempFile(data, sha1, ".pom")
    val future =
      Future(pomsReader.loadOne(tempFile).get)
        .flatMap { case (pom, _) => publishPom(pom, creationDate, userState) }
        .recover { cause =>
          logger.error(s"Invalid POM $path", cause)
          PublishResult.InvalidPom
        }
    future.onComplete(_ => filesystem.deleteTempFile(tempFile))
    future
  }

  private def publishPom(
      pom: ArtifactModel,
      creationDate: Instant,
      userState: Option[UserState]
  ): Future[PublishResult] = {
    val pomRef = s"${pom.groupId}:${pom.artifactId}:${pom.version}"
    val maybeProjectRef = githubExtractor.extract(pom)
    if (isPermittedToPublish(userState, maybeProjectRef)) {
      converter.convert(pom, maybeProjectRef, creationDate) match {
        case Some((artifact, deps)) =>
          insertArtifactWithDependencies(artifact, deps, maybeProjectRef, userState).map { _ =>
            logger.info(s"Published $pomRef")
            PublishResult.Success
          }
        case None =>
          logger.warn(s"Cannot convert $pomRef to a valid Scala artifact")
          Future.successful(PublishResult.InvalidPom)
      }
    } else {
      logger.warn(s"User ${userState.get.info.login} attempted to publish to $maybeProjectRef")
      Future.successful(PublishResult.Forbidden(userState.get.info.login, maybeProjectRef))
    }
  }

  private def insertArtifactWithDependencies(
      artifact: Artifact,
      dependencies: Seq[ArtifactDependency],
      maybeProjectRef: Option[Project.Reference],
      maybeUserState: Option[UserState]
  ): Future[Unit] =
    database.insertArtifact(artifact, dependencies, Instant.now).flatMap { isNewProject =>
      if (isNewProject && maybeProjectRef.isDefined && maybeUserState.isDefined)
        (maybeUserState, maybeProjectRef)
          .traverseN((userState, ref) => updateGithubInfo(new GithubClient(userState.info.token), ref, Instant.now))
          .map(_ => ())
      else Future.successful(())
    }

  private def isPermittedToPublish(
      maybeUserState: Option[UserState],
      maybeProjectRef: Option[Project.Reference]
  ): Boolean = {
    val userCanPublish = maybeUserState.map(_.hasPublishingAuthority(env)).fold(false)(identity)
    val userHasRepo = (maybeUserState, maybeProjectRef)
      .mapN((userState, projectRef) => userState.repos.contains(projectRef))
      .fold(false)(identity)
    // UserState may be empty if a publish is performed via a scheduler.
    maybeUserState.isEmpty || userCanPublish || userHasRepo
  }

  private def updateGithubInfo(githubClient: GithubClient, ref: Project.Reference, now: Instant): Future[Unit] =
    for {
      githubResponse <- githubClient.getProjectInfo(ref)
      _ <- database.updateGithubInfo(ref, githubResponse, now).failWithTry
    } yield ()

}

object PublishProcess {
  def apply(paths: DataPaths, filesystem: Storage, database: WebDatabase, env: Env)(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem
  ): PublishProcess = {
    val githubExtractor = new GithubRepoExtractor(paths)
    val converter = new ArtifactConverter(paths)
    val pomsReader = new PomsReader(new CoursierResolver)
    new PublishProcess(filesystem, githubExtractor, converter, database, pomsReader, env)
  }
}
