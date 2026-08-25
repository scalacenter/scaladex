package scaladex.server.route

import scala.concurrent.ExecutionContext

import scaladex.core.model.*
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase
import scaladex.core.util.ScalaExtensions.*
import scaladex.server.TwirlSupport.given
import scaladex.view.html.frontpage
import scaladex.view.model.EcosystemHighlight
import scaladex.view.model.EcosystemVersion

import cats.effect.ContextShift
import cats.effect.IO
import cats.syntax.parallel.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import play.twirl.api.HtmlFormat

class FrontPage(env: Env, database: WebDatabase, searchEngine: SearchEngine)(using ExecutionContext):
  private given ContextShift[IO] = IO.contextShift(summon[ExecutionContext])
  val limitOfProjects = 12

  def route(userState: Option[UserState]): Route = pathSingleSlash(frontPage(userState).completeIO)

  private def frontPage(userInfo: Option[UserState]): IO[HtmlFormat.Appendable] =
    (
      searchEngine.count().toIO,
      database.countArtifacts(),
      searchEngine.countByTopics(50).toIO,
      searchEngine.countByLanguages().toIO,
      searchEngine.countByPlatforms().toIO,
      searchEngine.getMostDependedUpon(limitOfProjects).toIO,
      searchEngine.getLatest(limitOfProjects).toIO
    ).parMapN { (totalProjects, totalArtifacts, topics, languages, platforms, mostDependedUpon, latestProjects) =>
      val scala3Ecosystem = EcosystemHighlight(
        "Scala",
        languages.collect {
          case (sv @ Scala.`3`, count) =>
            EcosystemVersion(Scala.`3`.version, count, Url(s"search?language=${sv.value}"))
        }
      )
      val scala2Ecosystem = EcosystemHighlight(
        "Scala",
        languages.collect {
          case (sv: Scala, count) if sv.version < Scala.`3`.version =>
            EcosystemVersion(sv.version, count, Url(s"search?language=${sv.value}"))
        }
      )
      val scalajsEcosystem = EcosystemHighlight(
        "Scala.js",
        platforms.collect {
          case (sjs: ScalaJs, count) =>
            EcosystemVersion(sjs.version, count, search = Url(s"search?platform=${sjs.value}"))
        }
      )
      val scalaNativeEcosystem = EcosystemHighlight(
        "Scala Native",
        platforms.collect {
          case (sn: ScalaNative, count) =>
            EcosystemVersion(sn.version, count, search = Url(s"search?platform=${sn.value}"))
        }
      )
      val sbtPluginEcosystem = EcosystemHighlight(
        "sbt",
        platforms.collect {
          case (sbtP: SbtPlugin, count) =>
            EcosystemVersion(sbtP.version, count, search = Url(s"search?platform=${sbtP.value}"))
        }
      )
      val millPluginEcosystem = EcosystemHighlight(
        "Mill",
        platforms.collect {
          case (millP: MillPlugin, count) =>
            EcosystemVersion(millP.version, count, search = Url(s"search?platform=${millP.value}"))
        }
      )

      frontpage(
        env,
        topics,
        Seq(scala3Ecosystem, scala2Ecosystem).flatten,
        Seq(scalajsEcosystem, scalaNativeEcosystem).flatten,
        Seq(sbtPluginEcosystem, millPluginEcosystem).flatten,
        latestProjects,
        mostDependedUpon,
        userInfo,
        totalProjects,
        totalArtifacts
      )
    }
end FrontPage
