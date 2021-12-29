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

class GithubUpdater(db: SchedulerDatabase, githubService: GithubService)(implicit ec: ExecutionContext)
    extends Scheduler("github-updater", 1.hour) {
  override def run(): Future[Unit] =
    db.getAllProjectStatuses().flatMap { projectStatuses =>
      val projectToUpdate =
        projectStatuses.collect { case (ref, status) if !status.moved && !status.notFound => ref }.toSeq

      logger.info(s"Updating github info of ${projectToUpdate.size} projects")
      projectToUpdate.mapSync(update).map(_ => ())
    }

  private def update(ref: Project.Reference): Future[Unit] = {
    val now = Instant.now()
    for {
      response <- githubService.getProjectInfo(ref)
      _ <- updateDbAndLog(ref, response, now)
    } yield ()
  }

  def updateDbAndLog(
      repo: Project.Reference,
      response: GithubResponse[(Project.Reference, GithubInfo)],
      now: Instant
  ): Future[Unit] =
    response match {
      case GithubResponse.Ok((_, info)) =>
        val status = GithubStatus.Ok(now)
        db.updateGithubInfoAndStatus(repo, info, status)

      case GithubResponse.MovedPermanently((destination, info)) =>
        val status = GithubStatus.Moved(now, destination)
        logger.info(s"$repo moved to $status")
        db.moveProject(repo, info, status)

      case GithubResponse.Failed(code, reason) =>
        val status =
          if (code == 404) GithubStatus.NotFound(now) else GithubStatus.Failed(now, code, reason)
        logger.info(s"Failed to download github info for $repo because of $status")
        db.updateGithubStatus(repo, status)
    }
}
