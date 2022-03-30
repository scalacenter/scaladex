package scaladex.server.service

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import scaladex.core.model.UserState
import scaladex.core.service.GithubService
import scaladex.core.service.SchedulerDatabase
import scaladex.infra.GithubClient

class UserSessionSynchronizer(database: SchedulerDatabase)(implicit ec: ExecutionContext, ac: ActorSystem)
    extends Scheduler("user-session-synchronizer", 1.hour) {

  type Session = (UUID, UserState)

  override def run(): Future[Unit] =
    for {
      sessions <- database.getAllSessions()
      sessionsWithClients = createClientsForEach(sessions)
      updatedEitherUserSessions <- sessionsWithClients.traverse {
        case (session, client) => updateUserSession(session, client)
      }
      expiredSessionIds = updatedEitherUserSessions.collect { case Left(expiredSessionId) => expiredSessionId }
      updatedUserSessions = updatedEitherUserSessions.collect { case Right(sessions) => sessions }
      _ <- updatedUserSessions.traverse { case (userId, userState) => database.insertSession(userId, userState) }
      _ <- expiredSessionIds.traverse(database.deleteSession)
    } yield ()

  private def createClientsForEach(sessions: Seq[Session]): Seq[(Session, GithubService)] =
    sessions.map { case session @ (_, userState) => (session, new GithubClient(userState.info.token)) }

  private def updateUserSession(session: Session, service: GithubService): Future[Either[UUID, Session]] =
    service
      .getUserInfo()
      .map { updatedUserInfo =>
        session match {
          case (userId, staleUserState) => Right((userId, staleUserState.copy(info = updatedUserInfo)))
        }
      }
      .recoverWith {
        case _ =>
          session match {
            case (userId, _) =>
              logger.info(s"Token for user with id: '$userId' is likely expired")
              Future(Left(userId))
          }
      }
}

object UserSessionSynchronizer {
  def apply(database: SchedulerDatabase)(implicit ec: ExecutionContext, ac: ActorSystem): UserSessionSynchronizer =
    new UserSessionSynchronizer(database)
}
