package scaladex.server.service

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import scaladex.core.model.GithubResponse
import scaladex.core.model.UserState
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.GithubClient

class UserSessionSynchronizer(database: SchedulerDatabase)(implicit ec: ExecutionContext, ac: ActorSystem)
    extends Scheduler("user-session-synchronizer", 1.hour) {

  type Session = (UUID, UserState)

  override def run(): Future[Unit] =
    database.getAllSessions().flatMap(_.mapSync(updateUserSession).map(_ => ()))

  private def updateUserSession(session: Session): Future[Unit] =
    session match {
      case (userId, userState) =>
        new GithubClient(userState.info.token).getUserState().flatMap {
          case GithubResponse.Ok(updatedState)               => database.insertSession(userId, updatedState)
          case GithubResponse.MovedPermanently(updatedState) => database.insertSession(userId, updatedState)
          case GithubResponse.Failed(code, errorMessage) =>
            if (code == StatusCodes.Unauthorized.intValue) {
              logger.info(s"Token for user with id: '$userId' is likely expired, with error: $errorMessage")
              database.deleteSession(userId)
            } else Future.successful(())
        }
    }
}

object UserSessionSynchronizer {
  def apply(database: SchedulerDatabase)(implicit ec: ExecutionContext, ac: ActorSystem): UserSessionSynchronizer =
    new UserSessionSynchronizer(database)
}
