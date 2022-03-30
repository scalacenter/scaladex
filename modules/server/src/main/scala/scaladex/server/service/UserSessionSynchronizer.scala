package scaladex.server.service

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.service.GithubService
import scaladex.core.service.SchedulerDatabase
import scaladex.infra.GithubClient

class UserSessionSynchronizer(database: SchedulerDatabase)(implicit ec: ExecutionContext, ac: ActorSystem)
    extends Scheduler("user-session-synchronizer", 1.hour) {

  override def run(): Future[Unit] =
    for {
      sessions <- database.getAllSessions()
      sessionsWithClients = createClientsForEach(sessions)
      updatedMaybeUserInfos <- sessionsWithClients.traverse { case (userId, session) => getUserInfo(userId, session) }
      updatedUserInfos = updatedMaybeUserInfos.flatten
      sessionsToDeleteIds = expiredSessionIds(sessions, updatedUserInfos)
      sessionsToUpdate = updatedSessions(sessions, updatedUserInfos)
      _ <- sessionsToUpdate.traverse { case (userId, userState) => database.insertSession(userId, userState) }
      _ <- sessionsToDeleteIds.traverse(database.deleteSession)
    } yield ()

  private def createClientsForEach(sessions: Seq[(UUID, UserState)]): Seq[(UUID, GithubService)] =
    sessions.map { case (userId, userState) => (userId, new GithubClient(userState.info.token)) }

  private def getUserInfo(userId: UUID, service: GithubService): Future[Option[(UUID, UserInfo)]] =
    service
      .getUserInfo()
      .map(userInfo => Option.apply((userId, userInfo)))
      .recoverWith {
        case _ =>
          logger.info(s"Token for user with id: '$userId' is likely expired")
          Future(None)
      }

  private def expiredSessionIds(
      storedSessions: Seq[(UUID, UserState)],
      updatedUserInfos: Seq[(UUID, UserInfo)]
  ): Seq[UUID] = {
    val storedSessionIds = storedSessions.map { case (userId, _) => userId }
    val successfullyUpdatedSessionIds = updatedUserInfos.map { case (userId, _) => userId }
    storedSessionIds.filterNot(successfullyUpdatedSessionIds.contains)
  }

  private def updatedSessions(
      storedSessions: Seq[(UUID, UserState)],
      updatedUserInfos: Seq[(UUID, UserInfo)]
  ): List[(UUID, UserState)] = {
    val storedSessionMap = storedSessions.toMap
    updatedUserInfos
      .foldLeft(Map[UUID, Option[UserState]]()) { (acc, userInfos) =>
        userInfos match {
          case (userId, updatedInfo) =>
            val maybeUpdatedState = storedSessionMap.get(userId).map(_.copy(info = updatedInfo))
            acc + (userId -> maybeUpdatedState)
        }
      }
      .toList
      .collect { case (userId, Some(userState)) => (userId, userState) }
  }
}

object UserSessionSynchronizer {
  def apply(database: SchedulerDatabase)(implicit ec: ExecutionContext, ac: ActorSystem): UserSessionSynchronizer =
    new UserSessionSynchronizer(database)
}
