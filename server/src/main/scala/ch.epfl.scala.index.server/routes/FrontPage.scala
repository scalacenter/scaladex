package ch.epfl.scala.index
package server
package routes

import model.misc.UserInfo

import TwirlSupport._
import GithubUserSessionDirective._

import akka.http.scaladsl.server.Directives._

class FrontPage(dataRepository: DataRepository, session: GithubUserSession) {
  import session.executionContext

  private def frontPage(userInfo: Option[UserInfo]) = {
    import dataRepository._
    for {
      keywords <- keywords()
      targets <- targets()
      mostDependedUpon <- mostDependedUpon()
      latestProjects <- latestProjects()
      latestReleases <- latestReleases()
    } yield
      views.html.frontpage(keywords, targets, latestProjects, mostDependedUpon, latestReleases, userInfo)
  }

  val routes =
    pathSingleSlash {
      githubUser(session) { user =>
        complete(frontPage(user.map(_.user)))
      }
    }
}
