package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.HtmlFormat
import scaladex.core.model.Env
import scaladex.core.model.MillPlugin
import scaladex.core.model.Platform
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
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
    val languagesF = searchEngine.countByLanguages()
    val platformsF = searchEngine.countByPlatforms()
    val mostDependedUponF = searchEngine.getMostDependedUpon(limitOfProjects)
    val latestProjectsF = searchEngine.getLatest(limitOfProjects)
    for {
      totalProjects <- totalProjectsF
      totalArtifacts <- totalArtifactsF
      topics <- topicsF
      platforms <- platformsF
      languages <- languagesF
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
        "Scala.js" -> "search?binaryVersions=sjs1",
        "Spark" -> query("topics")("spark"),
        "Typelevel" -> "typelevel"
      )

      val RecentScalaVersionsFirst: Ordering[(Scala, Int)] = Scala.ordering.reverse.on(_._1)

      val RecentPlatformVersionsFirst: Ordering[(Platform, Int)] =
        Platform.ordering.reverse.on(_._1)

      val scalaVersions = languages.collect { case (v: Scala, c) => (v, c) }.sorted(RecentScalaVersionsFirst)
      val scalaJsVersions = platforms.collect { case (v: ScalaJs, c) => (v, c) }.sorted(RecentPlatformVersionsFirst)
      val scalaNativeVersions =
        platforms.collect { case (v: ScalaNative, c) => (v, c) }.sorted(RecentPlatformVersionsFirst)
      val sbtVersions = platforms.collect { case (v: SbtPlugin, c) => (v, c) }.sorted(RecentPlatformVersionsFirst)
      val millVersions = platforms.collect { case (v: MillPlugin, c) => (v, c) }.sorted(RecentPlatformVersionsFirst)

      frontpage(
        env,
        topics,
        scalaVersions,
        scalaJsVersions,
        scalaNativeVersions,
        sbtVersions,
        millVersions,
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
