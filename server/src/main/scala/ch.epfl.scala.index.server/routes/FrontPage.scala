package ch.epfl.scala.index.server.routes

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.TwirlSupport._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import play.twirl.api.HtmlFormat
import scaladex.core.model.Artifact
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.UserState
import scaladex.core.service.WebDatabase
import scaladex.view.html.frontpage

class FrontPage(
    production: Boolean,
    db: WebDatabase,
    session: GithubUserSession
)(implicit ec: ExecutionContext) {
  import session.implicits._

  val limitOfProjectShownInFrontPage = 12

  private def frontPage(
      userInfo: Option[UserState]
  ): Future[HtmlFormat.Appendable] = {
    val topicsF = db.getAllTopics()
    val allPlatformsF = db.getAllPlatforms()
    val latestProjectsF = db.getLatestProjects(limitOfProjectShownInFrontPage)
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
      listOfProject <- db.getMostDependentUponProject(limitOfProjectShownInFrontPage)
      mostDependedUpon = listOfProject
        .sortBy(_._2)
        .reverse
        .map(_._1)
      latestProjects <- latestProjectsF
      latestReleases <- latestReleasesF
      totalProjects <- db.countProjects()
      totalReleases <- db.countArtifacts()
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
        production,
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
