package ch.epfl.scala.index
package server
package routes
package api

import ch.epfl.scala.index.api.Autocompletion

import model.misc.SearchParams
import model._, release._

import ch.megard.akka.http.cors.CorsDirectives._

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

class SearchApi(dataRepository: DataRepository)(implicit val executionContext: ExecutionContext) {
  val routes =
    pathPrefix("api") {
      cors() {
        path("search") {
          get {
            parameters('q, 'target, 'scalaVersion, 'scalaJsVersion.?, 'cli.as[Boolean] ? false) {
              (q, target0, scalaVersion0, targetVersion0, cli) =>
                val target1 =
                  (target0, SemanticVersion(scalaVersion0), targetVersion0.map(SemanticVersion(_))) match {
                    case ("JVM", Some(scalaVersion), _) =>
                      Some(ScalaTarget(scalaVersion))
                    case ("JS", Some(scalaVersion), Some(scalaJsVersion)) =>
                      Some(ScalaTarget(scalaVersion, scalaJsVersion))
                    // NATIVE
                    case _ =>
                      None
                  }

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
                  target1 match {
                    case Some(target) =>
                      (OK,
                       dataRepository
                         .find(SearchParams(queryString = q, targetFiltering = target1, cli = cli, total = 10))
                         .map { case (_, ps) => ps.map(p => convert(p)) }
                         .map(ps => write(ps)))
                    case None =>
                      (BadRequest, s"something is wrong: $target0 $scalaVersion0 $targetVersion0")
                  }
                )
            }
          }
        } ~
          path("project") {
            get {
              parameters('organization, 'repository, 'artifact.?) {
                (organization, repository, artifact) =>
                  val reference = Project.Reference(organization, repository)

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
                    dataRepository.projectPage(reference, ReleaseSelection(artifact, None)).map {
                      case Some((_, options)) => (OK, write(convert(options)))
                      case None => (NotFound, "")
                    })
              }
            }
          }
      } ~
        path("autocomplete") {
          get {
            parameter('q) { query =>
              complete {
                dataRepository.find(SearchParams(queryString = query, page = 1, sorting = None, total = 5)).map {
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
