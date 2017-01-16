package ch.epfl.scala.index
package server
package routes

import TwirlSupport._
import akka.http.scaladsl.server.Directives._

class FrontPage(dataRepository: DataRepository, session: GithubUserSession) {

  import session.executionContext

  private def frontPage(user: Option[UserState]) = {
    val userInfo = user.map(_.user)
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

  val routes = Routes.frontPagePath(session) (user => complete(frontPage(user)))
}
