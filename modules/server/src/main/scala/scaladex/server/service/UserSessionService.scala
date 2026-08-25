package scaladex.server.service

import java.util.UUID

import scaladex.core.model.GithubResponse
import scaladex.core.model.UserState
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*
import scaladex.core.util.Secret
import scaladex.infra.GithubClientImpl

import cats.effect.ContextShift
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.StatusCodes

class UserSessionService(database: SchedulerDatabase)(using system: ActorSystem) extends LazyLogging:
  private given ContextShift[IO] = IO.contextShift(system.dispatcher)

  def updateAll(): IO[String] =
    for
      sessions <- database.getAllUsers()
      responses <- sessions.mapIO { case (userId, userInfo) => updateUserSession(userId, userInfo.token) }
    yield
      val totalOk = responses.count(_.isOk)
      val totalMoved = responses.count(_.isMoved)
      val totalUnauthorized = responses.collect {
        case GithubResponse.Failed(code, _) if code == StatusCodes.Unauthorized.intValue => ()
      }.size
      val otherFailed = responses.count(_.isFailed) - totalUnauthorized
      s"Updated ${sessions.size} sessions: $totalOk OK, $totalMoved moved, $totalUnauthorized unauthorized, $otherFailed failures"

  private def updateUserSession(userId: UUID, token: Secret): IO[GithubResponse[UserState]] =
    val client = new GithubClientImpl(token)
    for
      response <- client.getUserState().toIO
      _ <- response match
        case GithubResponse.Ok(state) => database.updateUser(userId, state)
        case GithubResponse.MovedPermanently(state) => database.updateUser(userId, state)
        case GithubResponse.Failed(code, errorMessage) =>
          if code == StatusCodes.Unauthorized.intValue then
            logger.info(s"Token for user with id: '$userId' is likely expired, with error: $errorMessage")
            database.deleteUser(userId)
          else IO.unit
    yield response
    end for
  end updateUserSession
end UserSessionService
