package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import play.twirl.api.HtmlFormat
import scaladex.core.model.*
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport.*
import scaladex.view.html.frontpage
import scaladex.view.model.EcosystemHighlight
import scaladex.view.model.EcosystemVersion

class FrontPage(env: Env, database: WebDatabase, searchEngine: SearchEngine)(implicit ec: ExecutionContext):
  val limitOfProjects = 12

  def route(userState: Option[UserState]): Route = pathSingleSlash(complete(frontPage(userState)))

  private def frontPage(userInfo: Option[UserState]): Future[HtmlFormat.Appendable] =
    val totalProjectsF = searchEngine.count()
    val totalArtifactsF = database.countArtifacts()
    val topicsF = searchEngine.countByTopics(50)
    val languagesF = searchEngine.countByLanguages()
    val platformsF = searchEngine.countByPlatforms()
    val mostDependedUponF = searchEngine.getMostDependedUpon(limitOfProjects)
    val latestProjectsF = searchEngine.getLatest(limitOfProjects)
    for
      totalProjects <- totalProjectsF
      totalArtifacts <- totalArtifactsF
      topics <- topicsF
      platforms <- platformsF
      languages <- languagesF
      mostDependedUpon <- mostDependedUponF
      latestProjects <- latestProjectsF
    yield

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
    end for
  end frontPage
end FrontPage
