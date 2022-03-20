package scaladex.server

import java.util.UUID

import scala.collection.parallel.mutable.ParTrieMap
import scala.util.Try

import com.softwaremill.session._
import org.slf4j.LoggerFactory
import scaladex.core.model.UserState
import scaladex.core.service.WebDatabase

class GithubUserSession(sessionConfig: SessionConfig, database: WebDatabase) {
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

  private val users = ParTrieMap[UUID, UserState]()

  def addUser(userState: UserState): UUID = {
    val uuid = UUID.randomUUID
    users += uuid -> userState
    uuid
  }

  def getUser(id: UUID): Option[UserState] = users.get(id)
}
