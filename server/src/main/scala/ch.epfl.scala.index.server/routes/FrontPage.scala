package ch.epfl.scala.index
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
      topics <- topics()
      targetTypes <- targetTypes()
      scalaVersions <- scalaVersions()
      scalaJsVersions <- scalaJsVersions()
      scalaNativeVersions <- scalaNativeVersions()
      mostDependedUpon <- mostDependedUpon()
      latestProjects <- latestProjects()
      latestReleases <- latestReleases()
      totalProjects <- totalProjects()
      totalReleases <- totalReleases()
    } yield {

      def query(label: String)(xs: String*): String =
        xs.map(v => s"$label:$v").mkString("search?q=", " OR ", "")

      val ecosystems = Map(
        "Akka" -> query("topics")("akka",
                                    "akka-http",
                                    "akka-persistence",
                                    "akka-streams"),
        "Scala.js" -> "search?targets=scala.js_0.6",
        "Spark" -> query("depends-on")("apache/spark-streaming",
                                       "apache/spark-graphx",
                                       "apache/spark-hive",
                                       "apache/spark-mllib",
                                       "apache/spark-sql"),
        "Typelevel" -> "typelevel"
      )

      val excludedScalaVersions = Set("2.9", "2.8")

      views.html.frontpage(
        topics,
        targetTypes,
        scalaVersions.filterNot { case (version, _) => excludedScalaVersions.contains(version) },
        scalaJsVersions,
        scalaNativeVersions,
        latestProjects,
        mostDependedUpon,
        latestReleases,
        userInfo,
        ecosystems,
        totalProjects,
        totalReleases
      )
    }
  }

  val routes =
    pathSingleSlash {
      optionalSession(refreshable, usingCookies) { userId =>
        complete(frontPage(getUser(userId).map(_.user)))
      }
    }
}
