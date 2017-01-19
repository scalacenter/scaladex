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

      def query(label: String)(xs: String*): String = 
        xs.map(v => s"$label:$v").mkString("search?q=", " OR ", "")

      val ecosystems = Map(
        "Akka" -> query("keywords")("akka-extension", "akka-http-extension", "akka-persistence-plugin"),
        "Scala.js" -> "search?targets=scala.js_0.6",
        "Spark" -> query("depends-on")("apache/spark-streaming", "apache/spark-graphx", "apache/spark-hive", "apache/spark-mllib", "apache/spark-sql"),
        "Typelevel" -> "typelevel"
      )

      val excludeTargets = Set(
        "scala_2.9",
        "scala_2.8"
      )
      val targets0 = targets.filterNot{ case(target, _) => excludeTargets.contains(target)}
      views.html.frontpage(keywords, targets0, latestProjects, mostDependedUpon, latestReleases, userInfo, ecosystems)
    }
  }

  def frontPageBehavior(user: Option[UserState]) = {
    complete(frontPage(user))
  }
}
