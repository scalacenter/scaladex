package ch.epfl.scala.index
package server
package routes
package api

import play.api.libs.json._
import ch.epfl.scala.index.api.Autocompletion
import model.misc.SearchParams
import model._
import release._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import akka.http.scaladsl._
import akka.http.scaladsl.server.Route
import server.Directives._
import model.StatusCodes._

import scala.concurrent.ExecutionContext

object Api {

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
    dataRepository: DataRepository
)(implicit val executionContext: ExecutionContext)
    extends PlayJsonSupport {

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

                def convert(project: Project): Api.Project = {
                  import project._
                  val artifacts0 = if (cli) cliArtifacts.toList else artifacts
                  Api.Project(
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

                  def convert(options: ReleaseOptions): Api.ReleaseOptions = {
                    import options._
                    Api.ReleaseOptions(
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
          path("autocomplete") {
            get {
              parameter('q) { query =>
                complete {
                  dataRepository
                    .findProjects(
                      SearchParams(
                        queryString = query,
                        page = 1,
                        sorting = None,
                        total = 5
                      )
                    )
                    .map { page =>
                      page.items.map(
                        project =>
                          Autocompletion(
                            project.organization,
                            project.repository,
                            project.github.flatMap(_.description).getOrElse("")
                        )
                      )
                    }
                }
              }
            }
          }
      }
    }
}
