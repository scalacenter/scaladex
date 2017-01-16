package ch.epfl.scala.index
package server
package routes

import TwirlSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.scala.index.server.GithubUserSessionDirective.githubUser

class FrontPage(dataRepository: DataRepository, session: GithubUserSession) {

  private def frontPage(user: Option[UserState]) = {
    import session.executionContext
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

  private val userState = githubUser(session)

  val routes: Route = (Routes.frontPagePath & userState)(frontPageBehavior)

  private def frontPageBehavior(user: Option[UserState]) = {
    complete(frontPage(user))
  }
}
