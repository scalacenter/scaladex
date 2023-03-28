package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.service.GithubClient
import scaladex.core.service.WebDatabase
import scaladex.core.util.ScalaExtensions._

class GithubUpdater(database: WebDatabase, github: GithubClient)(implicit ec: ExecutionContext) extends LazyLogging {
  def updateAll(): Future[String] =
    database.getAllProjectsStatuses().flatMap { projectStatuses =>
      val projectToUpdate =
        projectStatuses
          .filter { case (_, status) => !status.isMoved }
          .toSeq
          .sortBy(_._2)
          .map(_._1)

      logger.info(s"Updating github info of ${projectToUpdate.size} projects")
      projectToUpdate.mapSync(update).map { statuses =>
        val totalOk = statuses.count(_.isOk)
        val totalNotFound = statuses.count(_.isNotFound)
        val totalFailed = statuses.count(_.isFailed)
        val totalMoved = statuses.count(_.isMoved)
        s"Updated ${projectToUpdate.size} projects: $totalOk OK, $totalNotFound Not Found, $totalFailed Failed, $totalMoved Moved"
      }
    }

  def update(ref: Project.Reference): Future[GithubStatus] =
    for {
      response <- github.getProjectInfo(ref)
      status <- updateGithubInfo(ref, response)
    } yield status

  private def updateGithubInfo(
      repo: Project.Reference,
      response: GithubResponse[(Project.Reference, GithubInfo)]
  ): Future[GithubStatus] = {
    val now = Instant.now()
    response match {
      case GithubResponse.Ok((_, info)) =>
        val status = GithubStatus.Ok(now)
        database.updateGithubInfoAndStatus(repo, info, status).map(_ => status)

      case GithubResponse.MovedPermanently((destination, info)) =>
        val status = GithubStatus.Moved(now, destination)
        logger.info(s"$repo moved to $status")
        database.moveProject(repo, info, status).map(_ => status)

      case GithubResponse.Failed(code, reason) =>
        val status =
          if (code == 404) GithubStatus.NotFound(now) else GithubStatus.Failed(now, code, reason)
        logger.info(s"Failed to download github info for $repo because of $status")
        database.updateGithubStatus(repo, status).map(_ => status)
    }
  }
}
