package ch.epfl.scala.index
package server
package routes

import views.search.html._

import TwirlSupport._

import akka.http.scaladsl._
import model._
import server.Directives._
import Uri._

class SearchPages(dataRepository: DataRepository, session: GithubUserSession) {

  import session.executionContext

  val valSearchPageRoute = Routes.searchPath(session) { (user, query, page, sorting, you) =>
        complete(
          dataRepository
            .find(query,
              page,
              sorting,
              you.flatMap(_ => user.map(_.repos)).getOrElse(Set()))
            .map {
              case (pagination, projects) =>
                searchresult(
                  query,
                  "search",
                  sorting,
                  pagination,
                  projects,
                  user.map(_.user),
                  you.isDefined
                )
            }
        )
      }

  val organizationRoute = Routes.organizationPath { organization =>
    val query = s"organization:$organization"
    redirect(s"/search?q=$query", StatusCodes.TemporaryRedirect)
  }

  val routes = get(valSearchPageRoute ~ organizationRoute)
}
