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

class GithubUpdater(database: SchedulerDatabase, githubService: GithubService)(implicit ec: ExecutionContext)
    extends Scheduler("github-updater", 1.hour) {
  override def run(): Future[Unit] =
    database.getAllProjectsStatuses().flatMap { projectStatuses =>
      val projectToUpdate =
        projectStatuses
          .filter { case (_, status) => !status.moved && !status.notFound }
          .toSeq
          .sortBy(_._2)
          .map(_._1)

      logger.info(s"Updating github info of ${projectToUpdate.size} projects")
      projectToUpdate.mapSync(update).map(_ => ())
    }

  private def update(ref: Project.Reference): Future[Unit] = {
    val now = Instant.now()
    for {
      response <- githubService.getProjectInfo(ref)
      _ <- database.updateGithubInfo(ref, response, now)
    } yield ()
  }
}
