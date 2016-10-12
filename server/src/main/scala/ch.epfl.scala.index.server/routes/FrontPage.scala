package ch.epfl.scala.index
package server
package routes

import model.misc.UserInfo

import TwirlSupport._

import com.softwaremill.session.{SessionManager, InMemoryRefreshTokenStorage}
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import akka.http.scaladsl.server.Directives._

import scala.concurrent.ExecutionContext
import scala.collection.parallel.mutable.ParTrieMap

import java.util.UUID

class FrontPage(dataRepository: DataRepository, users: ParTrieMap[UUID, UserState],
    implicit val sessionManager: SessionManager[UUID],
    implicit val refreshTokenStorage: InMemoryRefreshTokenStorage[UUID],
    implicit val executionContext: ExecutionContext) {

  private def frontPage(userInfo: Option[UserInfo]) = {
    import dataRepository._
    for {
      keywords       <- keywords()
      targets        <- targets()
      dependencies   <- dependencies()
      latestProjects <- latestProjects()
      latestReleases <- latestReleases()
    } yield views.html.frontpage(keywords, targets, dependencies, latestProjects, latestReleases, userInfo)
  }

  val routes =
    pathSingleSlash {
      optionalSession(refreshable, usingCookies) { userId =>
        complete(frontPage(userId.flatMap(users.get).map(_.user)))
      }
    }
}