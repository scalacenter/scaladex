package ch.epfl.scala.index
package server
package routes
package api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.scala.index.api.AutocompletionResponse
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.search.ProjectHit
import ch.epfl.scala.search.SearchParams
import ch.epfl.scala.services.SearchEngine
import ch.epfl.scala.services.WebDatabase
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.softwaremill.session.SessionDirectives.optionalSession
import com.softwaremill.session.SessionOptions.refreshable
import com.softwaremill.session.SessionOptions.usingCookies
import play.api.libs.json._

object SearchApi {
  implicit val formatProject: OFormat[Project] =
    Json.format[Project]

  implicit val formatReleaseOptions: OFormat[ReleaseOptions] =
    Json.format[ReleaseOptions]

  case class Project(
      organization: String,
      repository: String,
      logo: Option[String] = None,
      artifacts: Seq[String] = Nil
  )

  case class ReleaseOptions(
      artifacts: List[String],
      versions: List[String],
      groupId: String,
      artifactId: String,
      version: String
  )
  private[api] def parseScalaTarget(
      targetType: Option[String],
      scalaVersion: Option[String],
      scalaJsVersion: Option[String],
      scalaNativeVersion: Option[String],
      sbtVersion: Option[String]
  ): Option[Platform] =
    (
      targetType,
      scalaVersion.flatMap(ScalaLanguageVersion.tryParse),
      scalaJsVersion.flatMap(BinaryVersion.parse),
      scalaNativeVersion.flatMap(BinaryVersion.parse),
      sbtVersion.flatMap(BinaryVersion.parse)
    ) match {

      case (Some("JVM"), Some(scalaVersion), _, _, _) =>
        Some(Platform.ScalaJvm(scalaVersion))

      case (Some("JS"), Some(scalaVersion), Some(scalaJsVersion), _, _) =>
        Some(Platform.ScalaJs(scalaVersion, scalaJsVersion))

      case (
            Some("NATIVE"),
            Some(scalaVersion),
            _,
            Some(scalaNativeVersion),
            _
          ) =>
        Some(Platform.ScalaNative(scalaVersion, scalaNativeVersion))

      case (Some("SBT"), Some(scalaVersion), _, _, Some(sbtVersion)) =>
        Some(Platform.SbtPlugin(scalaVersion, sbtVersion))

      case (Some("Java"), None, None, None, None) => Some(Platform.Java)
      case _                                      => None
    }
}

class SearchApi(searchEngine: SearchEngine, db: WebDatabase, session: GithubUserSession)(
    implicit val executionContext: ExecutionContext
) extends PlayJsonSupport {
  import session.implicits._

  val routes: Route =
    pathPrefix("api") {
      cors() {
        path("search") {
          get {
            parameters(
              (
                "q",
                "target",
                "scalaVersion",
                "page".as[Int].?,
                "total".as[Int].?,
                "scalaJsVersion".?,
                "scalaNativeVersion".?,
                "sbtVersion".?,
                "cli".as[Boolean] ? false
              )
            ) {
              (
                  q,
                  targetType,
                  scalaVersion,
                  page,
                  total,
                  scalaJsVersion,
                  scalaNativeVersion,
                  sbtVersion,
                  cli
              ) =>
                val platform = SearchApi.parseScalaTarget(
                  Some(targetType),
                  Some(scalaVersion),
                  scalaJsVersion,
                  scalaNativeVersion,
                  sbtVersion
                )

                def convert(project: ProjectHit): SearchApi.Project = {
                  import project.document._
                  SearchApi.Project(
                    organization.value,
                    repository.value,
                    githubInfo.flatMap(_.logo.map(_.target)),
                    artifactNames.map(_.value)
                  )
                }

                platform match {
                  case Some(_) =>
                    val searchParams = SearchParams(
                      queryString = q,
                      targetFiltering = platform,
                      cli = cli,
                      page = page.getOrElse(0),
                      total = total.getOrElse(10)
                    )
                    val result = searchEngine
                      .find(searchParams)
                      .map(page => page.items.map(p => convert(p)))
                    complete(OK, result)

                  case None =>
                    val errorMessage =
                      s"something is wrong: $platform $scalaVersion $scalaJsVersion $scalaNativeVersion $sbtVersion"
                    complete(BadRequest, errorMessage)
                }
            }
          }
        } ~
          path("project") {
            get {
              parameters(
                (
                  "organization",
                  "repository",
                  "artifact".?,
                  "target".?,
                  "scalaVersion".?,
                  "scalaJsVersion".?,
                  "scalaNativeVersion".?,
                  "sbtVersion".?
                )
              ) {
                (
                    organization,
                    repository,
                    artifact,
                    targetType,
                    scalaVersion,
                    scalaJsVersion,
                    scalaNativeVersion,
                    sbtVersion
                ) =>
                  val reference =
                    NewProject.Reference.from(organization, repository)
                  val scalaTarget = SearchApi.parseScalaTarget(
                    targetType,
                    scalaVersion,
                    scalaJsVersion,
                    scalaNativeVersion,
                    sbtVersion
                  )
                  complete {
                    getReleaseOptions(reference, scalaTarget, artifact)
                  }
              }
            }
          } ~
          get {
            path("autocomplete") {
              optionalSession(refreshable, usingCookies) { userId =>
                val user = session.getUser(userId)
                searchParams(user) { params =>
                  complete {
                    autocomplete(params)
                  }
                }
              }
            }
          }
      }
    }

  private def getReleaseOptions(
      projectRef: NewProject.Reference,
      scalaTarget: Option[Platform],
      artifact: Option[String]
  ): Future[Option[SearchApi.ReleaseOptions]] = {
    val selection = new ReleaseSelection(
      target = scalaTarget,
      artifact = artifact.map(NewRelease.ArtifactName),
      version = None,
      selected = None
    )
    for {
      projectOpt <- db.findProject(projectRef)
      releases <- db.findReleases(projectRef)
    } yield for {
      project <- projectOpt
      filteredReleases = ReleaseOptions.filterReleases(selection, releases, project)
      selected <- filteredReleases.headOption
    } yield {
      val artifacts = filteredReleases.map(_.artifactName.value).toList
      val versions = filteredReleases.map(_.version.toString).toList
      SearchApi.ReleaseOptions(
        artifacts,
        versions,
        selected.maven.groupId,
        selected.maven.artifactId,
        selected.maven.version
      )
    }
  }

  private def autocomplete(params: SearchParams) =
    for (projects <- searchEngine.autocomplete(params))
      yield projects.map { project =>
        AutocompletionResponse(
          project.organization.value,
          project.repository.value,
          project.githubInfo.flatMap(_.description).getOrElse("")
        )
      }
}
