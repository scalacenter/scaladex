package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import play.twirl.api.HtmlFormat
import scaladex.core.model.Artifact
import scaladex.core.model.Env
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.UserState
import scaladex.core.service.WebDatabase
import scaladex.server.GithubUserSession
import scaladex.server.TwirlSupport._
import scaladex.view.html.frontpage

class FrontPage(
    env: Env,
    database: WebDatabase,
    session: GithubUserSession
)(implicit ec: ExecutionContext) {
  import session.implicits._

  val limitOfProjectShownInFrontPage = 12

  private def frontPage(
      userInfo: Option[UserState]
  ): Future[HtmlFormat.Appendable] = {
    val topicsF = database.getAllTopics()
    val allPlatformsF = database.getAllPlatforms()
    val latestProjectsF = database.getLatestProjects(limitOfProjectShownInFrontPage)
    val latestReleasesF = Future.successful(Seq.empty[Artifact]) // TODO get from DB
    val contributingProjectsF = Future.successful(List.empty[Project]) // TODO get from DB
    for {
      topics <- topicsF.map(FrontPage.getTopTopics(_, 50))
      allPlatforms <- allPlatformsF
      platformTypeWithCount = FrontPage.getPlatformTypeWithCount(allPlatforms)
      scalaFamilyWithCount = FrontPage.getScalaLanguageVersionWithCount(
        allPlatforms
      )
      scalaJsVersions = FrontPage
        .getPlatformWithCount(allPlatforms) {
          case p: Platform.ScalaJs =>
            p.scalaJsV
        }
      scalaNativeVersions = FrontPage
        .getPlatformWithCount(allPlatforms) {
          case p: Platform.ScalaNative =>
            p.scalaNativeV
        }
      sbtVersions = FrontPage
        .getPlatformWithCount(allPlatforms) {
          case p: Platform.SbtPlugin =>
            p.sbtV
        }
      listOfProjects <- database.getMostDependedUponProjects(limitOfProjectShownInFrontPage)
      mostDependedUpon = listOfProjects
        .sortBy { case (_, dependents) => -dependents }
        .map { case (project, _) => project }
      latestProjects <- latestProjectsF
      latestReleases <- latestReleasesF
      totalProjects <- database.countProjects()
      totalReleases <- database.countArtifacts()
      contributingProjects <- contributingProjectsF
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
        platformTypeWithCount,
        scalaFamilyWithCount,
        scalaJsVersions,
        scalaNativeVersions,
        sbtVersions,
        latestProjects,
        mostDependedUpon,
        latestReleases,
        userInfo,
        ecosystems,
        totalProjects,
        totalReleases,
        contributingProjects
      )
    }
  }

  val routes: Route =
    pathEndOrSingleSlash {
      optionalSession(refreshable, usingCookies)(userId => complete(frontPage(session.getUser(userId))))
    }
}
object FrontPage {
  def getTopTopics(topics: Seq[String], size: Int): List[(String, Int)] =
    topics
      .map(_.toLowerCase)
      .groupMapReduce(identity)(_ => 1)(_ + _)
      .toList
      .sortBy(-_._2)
      .take(size)
      .sortBy(_._1)

  override def hashCode(): Int = super.hashCode()

  def getPlatformTypeWithCount(
      platforms: Map[Project.Reference, Set[Platform]]
  ): List[(Platform.PlatformType, Int)] =
    getPlatformWithCount(platforms) {
      case platform: Platform =>
        platform.platformType
    }

  def getScalaLanguageVersionWithCount(
      platforms: Map[Project.Reference, Set[Platform]]
  ): List[(String, Int)] =
    getPlatformWithCount(platforms) {
      case platform: Platform if platform.scalaVersion.isDefined =>
        platform.scalaVersion.map(_.family).get
    }

  def getPlatformWithCount[A, B](
      platforms: Map[Project.Reference, Set[A]]
  )(
      collect: PartialFunction[A, B]
  )(implicit orderB: Ordering[(B, Int)]): List[(B, Int)] =
    platforms.values
      .flatMap(_.collect(collect))
      .groupMapReduce(identity)(_ => 1)(_ + _)
      .toList
      .sorted
}
