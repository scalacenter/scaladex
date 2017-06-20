package ch.epfl.scala.index
package server
package routes
package api

import ch.epfl.scala.index.api.Autocompletion

import model.misc.SearchParams
import model._, release._

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import akka.http.scaladsl._
import server.Directives._
import model.StatusCodes._

import upickle.default._

import scala.concurrent.ExecutionContext

object Api {
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

class SearchApi(dataRepository: DataRepository)(
    implicit val executionContext: ExecutionContext) {

  private def parseScalaTarget(
      targetType: Option[String],
      scalaVersion: Option[String],
      scalaJsVersion: Option[String],
      scalaNativeVersion: Option[String]): Option[ScalaTarget] = {
    (targetType,
     scalaVersion.flatMap(SemanticVersion.parse),
     scalaJsVersion.flatMap(SemanticVersion.parse),
     scalaNativeVersion.flatMap(SemanticVersion.parse)) match {

      case (Some("JVM"), Some(scalaVersion), _, _) =>
        Some(ScalaTarget.scala(scalaVersion.binary))

      case (Some("JS"), Some(scalaVersion), Some(scalaJsVersion), _) =>
        Some(ScalaTarget.scalaJs(scalaVersion.binary, scalaJsVersion.binary))

      case (Some("NATIVE"), Some(scalaVersion), _, Some(scalaNativeVersion)) =>
        Some(
          ScalaTarget.scalaNative(scalaVersion.binary,
                                  scalaNativeVersion.binary))

      case _ =>
        None
    }
  }

  val routes =
    pathPrefix("api") {
      cors() {
        path("search") {
          get {
            parameters(('q,
                        'target,
                        'scalaVersion,
                        'scalaJsVersion.?,
                        'scalaNativeVersion.?,
                        'cli.as[Boolean] ? false)) {

              (q,
               targetType,
               scalaVersion,
               scalaJsVersion,
               scalaNativeVersion,
               cli) =>
                val scalaTarget =
                  parseScalaTarget(Some(targetType),
                                   Some(scalaVersion),
                                   scalaJsVersion,
                                   scalaNativeVersion)

                def convert(project: Project): Api.Project = {
                  import project._

                  val artifacts0 =
                    if (cli) cliArtifacts.toList
                    else artifacts

                  Api.Project(organization,
                              repository,
                              project.github.flatMap(_.logo.map(_.target)),
                              artifacts0)
                }

                complete(
                  scalaTarget match {
                    case Some(target) =>
                      (OK,
                       dataRepository
                         .find(
                           SearchParams(queryString = q,
                                        targetFiltering = scalaTarget,
                                        cli = cli,
                                        total = 10))
                         .map { case (_, ps) => ps.map(p => convert(p)) }
                         .map(ps => write(ps)))
                    case None =>
                      (BadRequest,
                       s"something is wrong: $scalaTarget $scalaVersion $scalaJsVersion $scalaNativeVersion")
                  }
                )
            }
          }
        } ~
          path("project") {
            get {
              parameters(('organization,
                          'repository,
                          'artifact.?,
                          'target.?,
                          'scalaVersion.?,
                          'scalaJsVersion.?,
                          'scalaNativeVersion.?)) {
                (organization,
                 repository,
                 artifact,
                 targetType,
                 scalaVersion,
                 scalaJsVersion,
                 scalaNativeVersion) =>
                  val reference = Project.Reference(organization, repository)

                  val scalaTarget =
                    parseScalaTarget(targetType,
                                     scalaVersion,
                                     scalaJsVersion,
                                     scalaNativeVersion)

                  val selection = new ReleaseSelection(
                    target = scalaTarget,
                    artifact = artifact,
                    version = None
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
                    dataRepository.projectPage(reference, selection).map {
                      case Some((_, options)) => (OK, write(convert(options)))
                      case None               => (NotFound, "")
                    })
              }
            }
          } ~
          path("autocomplete") {
            get {
              parameter('q) { query =>
                complete {
                  dataRepository
                    .find(
                      SearchParams(queryString = query,
                                   page = 1,
                                   sorting = None,
                                   total = 5))
                    .map {
                      case (pagination, projects) =>
                        val summarisedProjects = projects.map(
                          p =>
                            Autocompletion(
                              p.organization,
                              p.repository,
                              p.github.flatMap(_.description).getOrElse("")
                          ))
                        write(summarisedProjects)
                    }
                }
              }
            }
          }
      }
    }
}
