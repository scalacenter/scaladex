package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.service.GithubClient
import scaladex.core.service.WebDatabase
import scaladex.infra.GithubException

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.pattern.CircuitBreakerOpenException

class GithubUpdater(database: WebDatabase, github: GithubClient)(using ExecutionContext) extends LazyLogging:
  def updateAll(): Future[String] =
    database.getAllProjectsStatuses().flatMap { projectStatuses =>
      val projectToUpdate =
        projectStatuses
          .filter { case (_, status) => !status.isMoved }
          .toSeq
          .sortBy(_._2)
          .map(_._1)

      logger.info(s"Updating github info of ${projectToUpdate.size} projects")
      updateSequentially(projectToUpdate.toList, Nil)
    }

  /** Update projects one by one, tolerating single-item failures and stopping early when the GitHub circuit breaker
    * opens (GitHub is rate limiting us or is down), so we don't fire thousands of doomed requests.
    */
  private def updateSequentially(remaining: List[Project.Reference], done: List[GithubStatus]): Future[String] =
    remaining match
      case Nil => Future.successful(summary(done, stoppedEarly = 0))
      case ref :: rest =>
        update(ref).transformWith {
          case Success(status) => updateSequentially(rest, status :: done)
          case Failure(_: CircuitBreakerOpenException) =>
            val notProcessed = remaining.size
            logger.error(
              s"GitHub circuit breaker is open (rate limited or unavailable): stopping github-info early, " +
                s"$notProcessed of ${done.size + notProcessed} projects not processed"
            )
            Future.successful(summary(done, stoppedEarly = notProcessed))
          case Failure(NonFatal(cause)) =>
            logger.warn(s"Failed to update github info for $ref, skipping", cause)
            val status = failedStatus(cause)
            database.updateGithubStatus(ref, status).transformWith(_ => updateSequentially(rest, status :: done))
        }

  private def failedStatus(cause: Throwable): GithubStatus =
    val now = Instant.now()
    cause match
      case e: GithubException => GithubStatus.Failed(now, e.code, e.errorMessage)
      case e => GithubStatus.Failed(now, -1, e.getMessage)

  private def summary(statuses: List[GithubStatus], stoppedEarly: Int): String =
    val totalOk = statuses.count(_.isOk)
    val totalNotFound = statuses.count(_.isNotFound)
    val totalFailed = statuses.count(_.isFailed)
    val totalMoved = statuses.count(_.isMoved)
    val base =
      s"Updated ${statuses.size} projects: $totalOk OK, $totalNotFound Not Found, $totalFailed Failed, $totalMoved Moved"
    if stoppedEarly > 0 then s"$base (stopped early, $stoppedEarly not processed)" else base

  def update(ref: Project.Reference): Future[GithubStatus] =
    for
      response <- github.getProjectInfo(ref)
      status <- updateGithubInfo(ref, response)
    yield status

  private def updateGithubInfo(
      repo: Project.Reference,
      response: GithubResponse[(Project.Reference, GithubInfo)]
  ): Future[GithubStatus] =
    val now = Instant.now()
    response match
      case GithubResponse.Ok((_, info)) =>
        val status = GithubStatus.Ok(now)
        database.updateGithubInfoAndStatus(repo, info, status).map(_ => status)

      case GithubResponse.MovedPermanently((destination, info)) =>
        val status = GithubStatus.Moved(now, destination)
        logger.info(s"$repo moved to $destination")
        database.moveProject(repo, info, status).map(_ => status)

      case GithubResponse.Failed(code, reason) =>
        val status =
          if code == 404 then GithubStatus.NotFound(now) else GithubStatus.Failed(now, code, reason)
        logger.info(s"Failed to download github info for $repo because of $status")
        database.updateGithubStatus(repo, status).map(_ => status)
    end match
  end updateGithubInfo
end GithubUpdater
