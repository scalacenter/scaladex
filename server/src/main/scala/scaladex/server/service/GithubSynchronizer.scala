package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.service.GithubService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._

class GithubSynchronizer(db: SchedulerDatabase, githubService: GithubService)(implicit ec: ExecutionContext)
    extends Scheduler("github-synchronizer", 1.hour) {
  override def run(): Future[Unit] =
    db.getAllProjects().flatMap { projects =>
      val filtered = projects.filterNot(_.githubStatus.movedOrNotFound)

      logger.info(s"Syncing github info of ${filtered.size} projects")
      filtered.mapSync(updateProject).map(_ => ())
    }

  private def updateProject(project: Project): Future[Unit] = {
    val now = Instant.now()
    for {
      githubInfosResponse <- githubService.getProjectInfo(project.reference)
      _ <- updateDbAndLog(project.reference, githubInfosResponse, now)
    } yield ()
  }

  def updateDbAndLog(
      repo: Project.Reference,
      githubInfosResponse: GithubResponse[GithubInfo],
      now: Instant
  ): Future[Unit] =
    githubInfosResponse match {
      case GithubResponse.Ok(info) =>
        val githubStatus = GithubStatus.Ok(now)
        db.updateGithubInfoAndStatus(repo, info, githubStatus)

      case GithubResponse.MovedPermanently(info) =>
        val githubStatus =
          GithubStatus.Moved(now, Project.Reference(info.organization, info.repository))
        logger.info(s"$repo moved to $githubStatus")
        db.createMovedProject(repo, info, githubStatus)

      case GithubResponse.Failed(code, reason) =>
        val githubStatus =
          if (code == 404) GithubStatus.NotFound(now) else GithubStatus.Failed(now, code, reason)
        logger.info(s"failed to download github info for $repo because of $githubStatus}")
        db.updateGithubStatus(repo, githubStatus)
    }
}
