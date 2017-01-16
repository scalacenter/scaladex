package ch.epfl.
scala.index
package server
package routes

import model.misc.UserInfo

import TwirlSupport._

import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import akka.http.scaladsl.server.Directives._

class FrontPage(dataRepository: DataRepository, session: GithubUserSession) {
  import session._

  private def frontPage(userInfo: Option[UserInfo]) = {
    import dataRepository._
    for {
      keywords <- keywords()
      targets <- targets()
      mostDependedUpon <- mostDependedUpon()
      latestProjects <- latestProjects()
      latestReleases <- latestReleases()
    } yield {
      val excludeTargets = Set(
        "scala_2.9",
        "scala_2.8"
      )
      val targets0 = targets.filterNot{ case(target, _) => excludeTargets.contains(target)}
      views.html.frontpage(keywords, targets0, latestProjects, mostDependedUpon, latestReleases, userInfo)
    }
  }

  val routes =
    pathSingleSlash {
      optionalSession(refreshable, usingCookies) { userId =>
        complete(frontPage(getUser(userId).map(_.user)))
      }
    }
}
