package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.HtmlFormat
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport._
import scaladex.view.html.frontpage

class FrontPage(env: Env, database: WebDatabase, searchEngine: SearchEngine)(implicit ec: ExecutionContext) {
  val limitOfProjects = 12

  def route(userState: Option[UserState]): Route = pathSingleSlash(complete(frontPage(userState)))

  private def frontPage(userInfo: Option[UserState]): Future[HtmlFormat.Appendable] = {
    val totalProjectsF = searchEngine.count()
    val totalArtifactsF = database.countArtifacts()
    val topicsF = searchEngine.countByTopics(50)
    val platformsF = searchEngine.countByPlatformTypes(10)
    val scalaVersionsF = searchEngine.countByScalaVersions(10)
    val scalaJsVersionsF = searchEngine.countByScalaJsVersions(10)
    val scalaNativeVersionsF = searchEngine.countByScalaNativeVersions(10)
    val sbtVersionsF = searchEngine.countBySbtVersison(10)
    val mostDependedUponF = searchEngine.getMostDependedUpon(limitOfProjects)
    val latestProjectsF = searchEngine.getLatest(limitOfProjects)
    for {
      totalProjects <- totalProjectsF
      totalArtifacts <- totalArtifactsF
      topics <- topicsF
      platforms <- platformsF
      scalaVersions <- scalaVersionsF
      scalaJsVersions <- scalaJsVersionsF
      scalaNativeVersions <- scalaNativeVersionsF
      sbtVersions <- sbtVersionsF
      mostDependedUpon <- mostDependedUponF
      latestProjects <- latestProjectsF
    } yield {

      def query(label: String)(xs: String*): String =
        xs.map(v => s"$label:$v").mkString("search?q=", " OR ", "")

      val ecosystems = Map(
        "Akka" -> query("topics")(
          "akka",
          "akka-http",
          "akka-persistence",
          "akka-streams"
        ),
        "Scala.js" -> "search?targets=scala.js_0.6",
        "Spark" -> query("topics")("spark"),
        "Typelevel" -> "typelevel"
      )

      frontpage(
        env,
        topics,
        platforms,
        scalaVersions,
        scalaJsVersions,
        scalaNativeVersions,
        sbtVersions,
        latestProjects,
        mostDependedUpon,
        userInfo,
        ecosystems,
        totalProjects,
        totalArtifacts
      )
    }
  }
}
