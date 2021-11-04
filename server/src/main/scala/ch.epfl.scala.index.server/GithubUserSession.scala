package ch.epfl.scala.index
package server

import java.util.UUID

import scala.collection.parallel.mutable.ParTrieMap
import scala.util.Try

import ch.epfl.scala.index.server.config.ServerConfig
import com.softwaremill.session._
import org.slf4j.LoggerFactory

class GithubUserSession(sessionConfig: SessionConfig) {
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

  def getUser(id: Option[UUID]): Option[UserState] = id.flatMap(users.get)
}

object GithubUserSession {
  def apply(config: ServerConfig): GithubUserSession =
    new GithubUserSession(config.session)
}
