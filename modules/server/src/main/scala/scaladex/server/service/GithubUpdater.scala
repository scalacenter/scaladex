package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext

import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.service.GithubClient
import scaladex.core.service.WebDatabase
import scaladex.core.util.ScalaExtensions.*

import cats.effect.ContextShift
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging

class GithubUpdater(database: WebDatabase, github: GithubClient)(using ExecutionContext) extends LazyLogging:
  private given ContextShift[IO] = IO.contextShift(summon[ExecutionContext])

  def updateAll(): IO[String] =
    database.getAllProjectsStatuses().flatMap { projectStatuses =>
      val projectToUpdate =
        projectStatuses
          .filter { case (_, status) => !status.isMoved }
          .toSeq
          .sortBy(_._2)
          .map(_._1)

      logger.info(s"Updating github info of ${projectToUpdate.size} projects")
      projectToUpdate.mapIO(update).map { statuses =>
        val totalOk = statuses.count(_.isOk)
        val totalNotFound = statuses.count(_.isNotFound)
        val totalFailed = statuses.count(_.isFailed)
        val totalMoved = statuses.count(_.isMoved)
        s"Updated ${projectToUpdate.size} projects: $totalOk OK, $totalNotFound Not Found, $totalFailed Failed, $totalMoved Moved"
      }
    }

  def update(ref: Project.Reference): IO[GithubStatus] =
    for
      response <- github.getProjectInfo(ref).toIO
      status <- updateGithubInfo(ref, response)
    yield status

  private def updateGithubInfo(
      repo: Project.Reference,
      response: GithubResponse[(Project.Reference, GithubInfo)]
  ): IO[GithubStatus] =
    val now = Instant.now()
    response match
      case GithubResponse.Ok((_, info)) =>
        val status = GithubStatus.Ok(now)
        database.updateGithubInfoAndStatus(repo, info, status).as(status)

      case GithubResponse.MovedPermanently((destination, info)) =>
        val status = GithubStatus.Moved(now, destination)
        logger.info(s"$repo moved to $destination")
        database.moveProject(repo, info, status).as(status)

      case GithubResponse.Failed(code, reason) =>
        val status =
          if code == 404 then GithubStatus.NotFound(now) else GithubStatus.Failed(now, code, reason)
        logger.info(s"Failed to download github info for $repo because of $status")
        database.updateGithubStatus(repo, status).as(status)
    end match
  end updateGithubInfo
end GithubUpdater
