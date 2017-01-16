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

  private def searchPageBehavior(user: Option[UserState], query: String, page: Int, sorting: Option[String], you: Option[String]) = {
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

  private def organizationBehavior(organization: String) = {
    val query = s"organization:$organization"
    redirect(s"/search?q=$query", StatusCodes.TemporaryRedirect)
  }

  val searchPageRoute = Routes.searchPath(session)(searchPageBehavior)

  val organizationRoute = Routes.organizationPath(organizationBehavior)

  val routes = get(searchPageRoute ~ organizationRoute)
}
