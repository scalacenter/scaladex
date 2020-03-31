package ch.epfl.scala.index
package server
package routes
package api

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.scala.index.api.AutocompletionResponse
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc.SearchParams
import ch.epfl.scala.index.model.release._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.softwaremill.session.SessionDirectives.optionalSession
import com.softwaremill.session.SessionOptions.{refreshable, usingCookies}
import play.api.libs.json._

import scala.concurrent.ExecutionContext

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
}

class SearchApi(
    dataRepository: DataRepository,
    session: GithubUserSession
)(implicit val executionContext: ExecutionContext)
    extends PlayJsonSupport {
  import session.implicits._

  private def parseScalaTarget(
      targetType: Option[String],
      scalaVersion: Option[String],
      scalaJsVersion: Option[String],
      scalaNativeVersion: Option[String],
      sbtVersion: Option[String]
  ): Option[ScalaTarget] = {
    (targetType,
     scalaVersion.flatMap(SemanticVersion.parse),
     scalaJsVersion.flatMap(SemanticVersion.parse),
     scalaNativeVersion.flatMap(SemanticVersion.parse),
     sbtVersion.flatMap(SemanticVersion.parse)) match {

      case (Some("JVM"), Some(scalaVersion), _, _, _) =>
        Some(ScalaTarget.scala(scalaVersion.binary))

      case (Some("JS"), Some(scalaVersion), Some(scalaJsVersion), _, _) =>
        Some(ScalaTarget.scalaJs(scalaVersion.binary, scalaJsVersion.binary))

      case (Some("NATIVE"),
            Some(scalaVersion),
            _,
            Some(scalaNativeVersion),
            _) =>
        Some(
          ScalaTarget.scalaNative(scalaVersion.binary,
                                  scalaNativeVersion.binary)
        )

      case (Some("SBT"), Some(scalaVersion), _, _, Some(sbtVersion)) =>
        Some(
          ScalaTarget.sbt(scalaVersion.binary, sbtVersion.binary)
        )

      case _ =>
        None
    }
  }

  val routes: Route =
    pathPrefix("api") {
      cors() {
        path("search") {
          get {
            parameters(
              ('q,
               'target,
               'scalaVersion,
               'page.as[Int].?,
               'total.as[Int].?,
               'scalaJsVersion.?,
               'scalaNativeVersion.?,
               'sbtVersion.?,
               'cli.as[Boolean] ? false)
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
                val scalaTarget = parseScalaTarget(
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
                ('organization,
                 'repository,
                 'artifact.?,
                 'target.?,
                 'scalaVersion.?,
                 'scalaJsVersion.?,
                 'scalaNativeVersion.?,
                 'sbtVersion.?)
              ) {
                (organization,
                 repository,
                 artifact,
                 targetType,
                 scalaVersion,
                 scalaJsVersion,
                 scalaNativeVersion,
                 sbtVersion) =>
                  val reference = Project.Reference(organization, repository)

                  val scalaTarget =
                    parseScalaTarget(targetType,
                                     scalaVersion,
                                     scalaJsVersion,
                                     scalaNativeVersion,
                                     sbtVersion)

                  val selection = new ReleaseSelection(
                    target = scalaTarget,
                    artifact = artifact,
                    version = None,
                    selected = None
                  )

                  def convert(
                      options: ReleaseOptions
                  ): SearchApi.ReleaseOptions = {
                    import options._
                    SearchApi.ReleaseOptions(
                      artifacts,
                      versions.sorted.map(_.toString),
                      release.maven.groupId,
                      release.maven.artifactId,
                      release.maven.version
                    )
                  }

                  complete(
                    dataRepository
                      .getProjectPage(reference, selection)
                      .map(
                        _.map { case (_, options) => convert(options) }
                      )
                  )
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

  private def autocomplete(params: SearchParams) = {
    for (projects <- dataRepository.autocompleteProjects(params))
      yield
        projects.map { project =>
          AutocompletionResponse(
            project.organization,
            project.repository,
            project.github.flatMap(_.description).getOrElse("")
          )
        }
  }
}
