package ch.epfl.scala.index
package server
package routes
package api

import ch.epfl.scala.index.api.Autocompletion

import akka.http.scaladsl.server.Directives._

import upickle.default.{write => uwrite}

import scala.concurrent.ExecutionContext

class ApiRouting(dataRepository: DataRepository)(implicit val executionContext: ExecutionContext) {
  val route = 
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
      }
    }
}