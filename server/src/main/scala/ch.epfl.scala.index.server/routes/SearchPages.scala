package ch.epfl.scala.index
package server
package routes

import views.search.html._

import com.softwaremill.session._, SessionDirectives._, SessionOptions._

import TwirlSupport._

import akka.http.scaladsl._
import model._
import server.Directives._
import Uri._

class SearchPages(dataRepository: DataRepository, session: GithubUserSession) {
  import session._
  import dataRepository._

  val routes =
    get {
      path("search") {
        optionalSession(refreshable, usingCookies) { userId =>
          parameters('q, 'page.as[Int] ? 1, 'sort.?, 'you.?) { (query, page, sorting, you) =>
            
            val userRepos = you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set()) 
            
            complete(
              for {
                (pagination, projects) <- find(query, page, sorting, userRepos)
                keywords <- keywords(Some(query))
                targets <- targets(Some(query))
              } yield {
                searchresult(
                  query,
                  "search",
                  sorting,
                  pagination,
                  projects,
                  getUser(userId).map(_.user),
                  you.isDefined,
                  keywords,
                  targets
                )
              }
            )
          }
        }
      } ~
        path(Segment) { organization =>
          val query = s"organization:$organization"
          redirect(s"/search?q=$query", StatusCodes.TemporaryRedirect)
        }
    }
}
