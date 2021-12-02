package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Try

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.services.GithubService
import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.utils.ScalaExtensions._

class GithubSynchronizer(db: SchedulerDatabase, githubService: GithubService)(implicit ec: ExecutionContext)
    extends Scheduler("github-synchronizer", 1.hour) {
  override def run(): Future[Unit] =
    db.getAllProjects().flatMap { projects =>
      logger.info(s"Syncing github info of ${projects.size} projects")
      projects.grouped(10).toSeq.mapSync(updateProjects).map(_ => ())
    }

  private def updateProjects(projects: Seq[NewProject]): Future[Unit] =
    for {
      githubInfosTry <- projects
        .map(p => githubService.update(p.githubRepo).failWithTry.map((p.reference, _)))
        .sequence
      _ = logFailures(githubInfosTry)
      _ <- githubInfosTry.map {
        case (repo, ghInfoTry) =>
          ghInfoTry
            .map(db.updateGithubInfo(repo, _, Instant.now()))
            .getOrElse(Future.successful(()))
      }.sequence
    } yield ()

  private def logFailures(res: Seq[(NewProject.Reference, Try[GithubInfo])]): Unit =
    res.collect {
      case (githubRepo, Failure(exception)) =>
        logger.warn(
          s"failed to download github info for $githubRepo because of ${exception.getMessage}"
        )
    }
}
