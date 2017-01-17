package ch.epfl.
scala.index
package server
package routes

import TwirlSupport._
import akka.http.scaladsl.server.Directives._

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
    } yield {
      val excludeTargets = Set(
        "scala_2.9",
        "scala_2.8"
      )
      val targets0 = targets.filterNot{ case(target, _) => excludeTargets.contains(target)}
      views.html.frontpage(keywords, targets0, latestProjects, mostDependedUpon, latestReleases, userInfo)
    }
  }

  def frontPageBehavior(user: Option[UserState]) = {
    complete(frontPage(user))
  }
}
