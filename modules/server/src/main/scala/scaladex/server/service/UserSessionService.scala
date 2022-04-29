package scaladex.server.service

import java.util.UUID

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.GithubResponse
import scaladex.core.model.UserState
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.GithubClientImpl

class UserSessionService(database: SchedulerDatabase)(implicit system: ActorSystem) extends LazyLogging {
  import system.dispatcher

  def updateAll(): Future[String] =
    for {
      sessions <- database.getAllSessions()
      responses <- sessions.mapSync { case (userId, userState) => updateUserSession(userId, userState) }
    } yield {
      val totalOk = responses.count(_.isOk)
      val totalMoved = responses.count(_.isMoved)
      val totalUnauthorized = responses.collect {
        case GithubResponse.Failed(code, _) if code == StatusCodes.Unauthorized.intValue => ()
      }.size
      val otherFailed = responses.count(_.isFailed) - totalUnauthorized
      s"Updated ${sessions.size} sessions: $totalOk OK, $totalMoved moved, $totalUnauthorized unauthorized, $otherFailed failures"
    }

  private def updateUserSession(userId: UUID, userState: UserState): Future[GithubResponse[UserState]] = {
    val client = new GithubClientImpl(userState.info.token)
    for {
      response <- client.getUserState()
      _ <- response match {
        case GithubResponse.Ok(updatedState)               => database.insertSession(userId, updatedState)
        case GithubResponse.MovedPermanently(updatedState) => database.insertSession(userId, updatedState)
        case GithubResponse.Failed(code, errorMessage) =>
          if (code == StatusCodes.Unauthorized.intValue) {
            logger.info(s"Token for user with id: '$userId' is likely expired, with error: $errorMessage")
            database.deleteSession(userId)
          } else Future.successful(())
      }
    } yield response
  }
}
