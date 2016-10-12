package ch.epfl.scala.index
package server
package routes

import views.html._

import com.softwaremill.session._, SessionDirectives._, SessionOptions._

import TwirlSupport._

import akka.http.scaladsl._
import model._
import server.Directives._
import Uri._

class SearchPages(dataRepository: DataRepository, session: GithubUserSession){
  import session._

  val routes =
    get {
      path("search") {
        optionalSession(refreshable, usingCookies) { userId =>
          parameters('q, 'page.as[Int] ? 1, 'sort.?, 'you.?) { (query, page, sorting, you) =>
            complete(
              dataRepository
                .find(query, page, sorting, you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set()))
                .map {
                  case (pagination, projects) =>
                    searchresult(
                      query,
                      "search",
                      sorting,
                      pagination,
                      projects,
                      getUser(userId).map(_.user),
                      !you.isEmpty
                    )
                }
            )
          }
        }
      } ~
      path(Segment) { organization =>
        optionalSession(refreshable, usingCookies) { userId =>
          parameters('page.as[Int] ? 1, 'sort.?) { (page, sorting) =>
            pathEnd {
              val query = s"organization:$organization"
              complete(
                dataRepository.find(query, page, sorting).map {
                  case (pagination, projects) =>
                    searchresult(query,
                      organization,
                      sorting,
                      pagination,
                      projects,
                      getUser(userId).map(_.user),
                      you = false
                    )
                }
              )
            }
          }
        }
      }
    }
}


