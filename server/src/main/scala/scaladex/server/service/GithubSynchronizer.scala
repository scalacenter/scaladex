package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubResponse
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.services.GithubService
import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.utils.ScalaExtensions._

class GithubSynchronizer(db: SchedulerDatabase, githubService: GithubService)(implicit ec: ExecutionContext)
    extends Scheduler("github-synchronizer", 1.hour) {
  override def run(): Future[Unit] =
    db.getAllProjects().flatMap { projects =>
      val filtered = projects.filterNot(_.githubStatus.movedOrNotFound)

      logger.info(s"Syncing github info of ${filtered.size} projects")
      filtered.mapSync(updateProject).map(_ => ())
    }

  private def updateProject(project: NewProject): Future[Unit] = {
    val now = Instant.now()
    for {
      githubInfosResponse <- githubService.update(project.githubRepo)
      _ <- updateDbAndLog(project.reference, githubInfosResponse, now)
    } yield ()
  }

  def updateDbAndLog(
      repo: NewProject.Reference,
      githubInfosResponse: GithubResponse[GithubInfo],
      now: Instant
  ): Future[Unit] =
    githubInfosResponse match {
      case GithubResponse.Ok(info) =>
        val githubStatus = GithubStatus.Ok(now)
        db.updateGithubInfoAndStatus(repo, info, githubStatus)

      case GithubResponse.MovedPermanently(info) =>
        val githubStatus =
          GithubStatus.Moved(now, NewProject.Organization(info.owner), NewProject.Repository(info.name))
        logger.info(s"$repo moved to $githubStatus")
        db.createMovedProject(repo, info, githubStatus)

      case GithubResponse.Failed(code, reason) =>
        val githubStatus =
          if (code == 404) GithubStatus.NotFound(now) else GithubStatus.Failed(now, code, reason)
        logger.info(s"failed to download github info for $repo because of $GithubStatus}")
        db.updateGithubStatus(repo, githubStatus)
    }
}
