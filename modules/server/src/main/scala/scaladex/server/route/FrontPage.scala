package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.HtmlFormat
import scaladex.core.model._
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
      topics <- topicsF.map(_.map { case (topic, count) => TopicCount(topic, count, None) })
      platforms <- platformsF
      languages <- languagesF
      mostDependedUpon <- mostDependedUponF
      latestProjects <- latestProjectsF
    } yield {

      def query(label: String)(xs: String*): Url =
        Url(xs.map(v => s"$label:$v").mkString("search?q=", " OR ", ""))

      val ecosystems = List(
        "Akka" -> query("topics")(
          "akka",
          "akka-http",
          "akka-persistence",
          "akka-streams"
        ),
        "Spark" -> query("topics")("spark"),
        "Typelevel" -> Url("search?q=typelevel")
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

      def selectCurrent(allVersions: Seq[EcosystemVersion]): Option[(EcosystemVersion, Seq[EcosystemVersion])] =
        allVersions.filterNot(_.deprecated).headOption.map { currentVersion =>
          val otherVersions = allVersions.filterNot(_ == currentVersion)
          currentVersion -> otherVersions
        }

      val scalajsEcosystem = {
        val allVersions = scalaJsVersions.map {
          case (sjs, cnt) =>
            EcosystemVersion(
              version = sjs.version,
              deprecated = sjs.isDeprecated,
              libraryCount = cnt,
              search = Url(s"search?platforms=${sjs.label}")
            )
        }
        selectCurrent(allVersions).map {
          case (currentVersion, otherVersions) =>
            EcosystemHighlight(
              ecosystem = "Scala.js",
              currentVersion = currentVersion,
              otherVersions = otherVersions
            )
        }
      }

      val (scala2, scala3) = scalaVersions
        .map {
          case (scalaVersion, count) =>
            EcosystemVersion(
              version = scalaVersion.version,
              deprecated = scalaVersion.isDeprecated,
              libraryCount = count,
              search = Url(s"search?languages=${scalaVersion.label}")
            )
        }
        .partition(_.version < Scala.`3`.version)

      val scala3Ecosystem =
        selectCurrent(scala3).map {
          case (current, other) =>
            EcosystemHighlight(
              ecosystem = "Scala",
              currentVersion = current,
              otherVersions = other
            )
        }

      val scala2Ecosystem =
        selectCurrent(scala2).map {
          case (current, other) =>
            EcosystemHighlight(
              ecosystem = "Scala",
              currentVersion = current,
              otherVersions = other
            )
        }

      val snEcosystem = {
        val allVersions = scalaNativeVersions.map {
          case (sn, cnt) =>
            EcosystemVersion(
              version = sn.version,
              deprecated = sn.isDeprecated,
              libraryCount = cnt,
              search = Url(s"search?platforms=${sn.label}")
            )
        }
        selectCurrent(allVersions).map {
          case (currentVersion, otherVersions) =>
            EcosystemHighlight(
              ecosystem = "Scala Native",
              currentVersion = currentVersion,
              otherVersions = otherVersions
            )
        }
      }

      val sbtPluginEcosystem = {
        val allVersions = sbtVersions.map {
          case (sbt, cnt) =>
            EcosystemVersion(
              version = sbt.version,
              deprecated = sbt.isDeprecated,
              libraryCount = cnt,
              search = Url(s"search?platforms=${sbt.label}")
            )
        }
        selectCurrent(allVersions).map {
          case (currentVersion, otherVersions) =>
            EcosystemHighlight(
              ecosystem = "sbt",
              currentVersion = currentVersion,
              otherVersions = otherVersions,
              logo = None
            )
        }
      }

      val millPluginEcosystem = {
        val allVersions = millVersions.map {
          case (mill, cnt) =>
            EcosystemVersion(
              version = mill.version,
              deprecated = mill.isDeprecated,
              libraryCount = cnt,
              search = Url(s"search?platforms=${mill.label}")
            )
        }
        selectCurrent(allVersions).map {
          case (currentVersion, otherVersions) =>
            EcosystemHighlight(
              ecosystem = "Mill",
              currentVersion = currentVersion,
              otherVersions = otherVersions,
              logo = None
            )
        }
      }

      frontpage(
        env,
        topics,
        Seq(scala3Ecosystem, scala2Ecosystem).flatten,
        Seq(scalajsEcosystem, snEcosystem).flatten,
        Seq(sbtPluginEcosystem, millPluginEcosystem).flatten,
        ecosystems,
        latestProjects,
        mostDependedUpon,
        userInfo,
        totalProjects,
        totalArtifacts
      )
    }
  }
}
