package scaladex.server

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.softwaremill.session._
import org.slf4j.LoggerFactory
import scaladex.core.model.UserState
import scaladex.core.service.WebDatabase

class GithubUserSession(sessionConfig: SessionConfig, database: WebDatabase)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  object implicits {
    implicit def serializer: SessionSerializer[UUID, String] =
      new SingleValueSessionSerializer(
        _.toString(),
        (id: String) => Try(UUID.fromString(id))
      )
    implicit val sessionManager: SessionManager[UUID] =
      new SessionManager[UUID](sessionConfig)
    implicit val refreshTokenStorage: InMemoryRefreshTokenStorage[UUID] =
      (msg: String) =>
        if (msg.startsWith("Looking up token for selector")) () // borring
        else logger.info(msg)
  }

  def addUser(userState: UserState): Future[UUID] = {
    val userId = UUID.randomUUID
    database.insertSession(userId, userState).map(_ => userId)
  }

  def getUser(id: UUID): Future[Option[UserState]] =
    database.getSession(id)
}
