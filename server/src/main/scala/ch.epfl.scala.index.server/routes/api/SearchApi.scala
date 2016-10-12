package ch.epfl.scala.index
package server
package routes
package api

import ch.epfl.scala.index.api.Autocompletion

import model._, release._

import ch.megard.akka.http.cors.CorsDirectives._

import akka.http.scaladsl._
import server.Directives._
import model.StatusCodes._

import upickle.default.{write => uwrite}

import scala.concurrent.ExecutionContext

class SearchApi(dataRepository: DataRepository)(implicit val executionContext: ExecutionContext) {
  val routes = 
    pathPrefix("api") {
      path("search") {
        get {
          parameter('q) { query =>
            complete {
              dataRepository.find(query, page = 1, sorting = None, total = 5).map {
                case (pagination, projects) =>
                  val summarisedProjects = projects.map(p =>
                        Autocompletion(
                            p.organization,
                            p.repository,
                            p.github.flatMap(_.description).getOrElse("")
                      ))
                  uwrite(summarisedProjects)
              }
            }
          }
        }
      } ~
      path("scastie") {
        get {
          cors() {
            parameters('q, 'target, 'scalaVersion, 'targetVersion.?) { 
              (q, target0, scalaVersion0, targetVersion0) =>

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

              def convert(project: Project): ScastieProject = {
                import project._
                ScastieProject(organization, repository, project.github.flatMap(_.logo.map(_.target)), artifacts)
              }

              complete(
                target1 match {
                  case Some(target) => 
                    (OK, dataRepository
                          .find(q, targetFiltering = target1)
                          .map{case (_, ps) => ps.map(p => convert(p))}
                          .map(ps => uwrite(ps))
                    )
                  case None => (BadRequest, s"something is wrong: $target0 $scalaVersion0 $targetVersion0")
                }
              )
            }
          }
        }
      }
    }
}
