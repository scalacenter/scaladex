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
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc.SearchParams
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.search.ESRepo
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
      artifacts: List[String] = Nil
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
  ): Option[ScalaTarget] = {
    (
      targetType,
      scalaVersion.flatMap(LanguageVersion.tryParse),
      scalaJsVersion.flatMap(BinaryVersion.parse),
      scalaNativeVersion.flatMap(BinaryVersion.parse),
      sbtVersion.flatMap(BinaryVersion.parse)
    ) match {

      case (Some("JVM"), Some(scalaVersion), _, _, _) =>
        Some(ScalaJvm(scalaVersion))

      case (Some("JS"), Some(scalaVersion), Some(scalaJsVersion), _, _) =>
        Some(ScalaJs(scalaVersion, scalaJsVersion))

      case (
            Some("NATIVE"),
            Some(scalaVersion),
            _,
            Some(scalaNativeVersion),
            _
          ) =>
        Some(ScalaNative(scalaVersion, scalaNativeVersion))

      case (Some("SBT"), Some(scalaVersion), _, _, Some(sbtVersion)) =>
        Some(SbtPlugin(scalaVersion, sbtVersion))

      case _ => None
    }
  }
}

class SearchApi(
    dataRepository: ESRepo,
    session: GithubUserSession
)(implicit val executionContext: ExecutionContext)
    extends PlayJsonSupport {
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
                val scalaTarget = SearchApi.parseScalaTarget(
                  Some(targetType),
                  Some(scalaVersion),
                  scalaJsVersion,
                  scalaNativeVersion,
                  sbtVersion
                )

                def convert(project: Project): SearchApi.Project = {
                  import project._
                  val artifacts0 = if (cli) cliArtifacts.toList else artifacts
                  SearchApi.Project(
                    organization,
                    repository,
                    project.github.flatMap(_.logo.map(_.target)),
                    artifacts0
                  )
                }

                scalaTarget match {
                  case Some(_) =>
                    val searchParams = SearchParams(
                      queryString = q,
                      targetFiltering = scalaTarget,
                      cli = cli,
                      page = page.getOrElse(0),
                      total = total.getOrElse(10)
                    )
                    val result = dataRepository
                      .findProjects(searchParams)
                      .map(page => page.items.map(p => convert(p)))
                    complete(OK, result)

                  case None =>
                    val errorMessage =
                      s"something is wrong: $scalaTarget $scalaVersion $scalaJsVersion $scalaNativeVersion $sbtVersion"
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
                  val reference = Project.Reference(organization, repository)
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
      projectRef: Project.Reference,
      scalaTarget: Option[ScalaTarget],
      artifact: Option[String]
  ): Future[Option[SearchApi.ReleaseOptions]] = {
    for {
      projectAndReleaseOptions <- dataRepository.getProjectAndReleaseOptions(
        projectRef,
        new ReleaseSelection(
          target = scalaTarget,
          artifact = artifact,
          version = None,
          selected = None
        )
      )
    } yield {
      projectAndReleaseOptions
        .map { case (_, options) =>
          SearchApi.ReleaseOptions(
            options.artifacts,
            options.versions.sorted.map(_.toString),
            options.release.maven.groupId,
            options.release.maven.artifactId,
            options.release.maven.version
          )
        }
    }
  }

  private def autocomplete(params: SearchParams) = {
    for (projects <- dataRepository.autocompleteProjects(params))
      yield projects.map { project =>
        AutocompletionResponse(
          project.organization,
          project.repository,
          project.github.flatMap(_.description).getOrElse("")
        )
      }
  }
}
