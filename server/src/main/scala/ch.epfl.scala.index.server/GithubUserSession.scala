package ch.epfl.scala.index
package server

import com.typesafe.config._

import com.softwaremill.session._
// import com.softwaremill.session.{SessionManager, InMemoryRefreshTokenStorage}

import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.ExecutionContext

import scala.util.Try
import java.util.UUID

class GithubUserSession(config: Config)(implicit val executionContext: ExecutionContext) {

  val sessionConfig = SessionConfig.default(config.getString("sesssion-secret"))

  implicit def serializer: SessionSerializer[UUID, String] =
    new SingleValueSessionSerializer(
      _.toString(),
      (id: String) => Try { UUID.fromString(id) }
    )
  implicit val sessionManager = new SessionManager[UUID](sessionConfig)
  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UUID] {
    def log(msg: String) =
      if (msg.startsWith("Looking up token for selector")) () // borring
      else println(msg)
  }

  private val users = ParTrieMap[UUID, UserState]()

  def addUser(userState: UserState): UUID = {
    val uuid = UUID.randomUUID
    users += uuid -> userState
    uuid
  }

  def getUser(id: Option[UUID]): Option[UserState] = id.flatMap(users.get)
}
