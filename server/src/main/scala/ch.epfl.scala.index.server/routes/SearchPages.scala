package ch.epfl.scala.index
package server
package routes

import views.search.html._

import model.release.ScalaTarget
import model.misc.SearchParams

import TwirlSupport._

import akka.http.scaladsl._
import model._
import server._
import server.Directives._
import Uri._

class SearchPages(dataRepository: DataRepository, session: GithubUserSession) {

  import session.executionContext

  private def search(params: SearchParams, user: Option[UserState], uri: String) = {
    import dataRepository._
    complete(
      for {
        (pagination, projects) <- find(params)
        keywords <- keywords(params)
        targets <- targets(params)
      } yield {

        val parsedTargets =
          targets.toList.map{case (name, count) =>
            ScalaTarget.fromSupportName(name).map{ case (label, version) => (label, version, count, name)}
          }.flatten.sorted

        searchresult(
          params,
          uri,
          pagination,
          projects,
          user.map(_.user),
          params.userRepos.nonEmpty,
          keywords,
          parsedTargets
        )
      }
    )
  }

  def searchParams(user: Option[UserState]): Directive1[SearchParams] =
    parameters(('q ? "*", 'page.as[Int] ? 1, 'sort.?, 'keywords.*, 'targets.*, 'you.?)).tmap{
          case ( q      ,  page            ,  sort  ,  keywords  ,  targets  ,  you) =>

      val userRepos = you.flatMap(_ => user.map(_.repos)).getOrElse(Set())
      SearchParams(
        q,
        page,
        sort,
        userRepos,
        keywords = keywords.toList,
        targets  = targets.toList
      )
    }

  private val searchPath = "search"

  def searchPageBehavior(user: Option[UserState], query: String, page: Int, sorting: Option[String], you: Option[String]): Route = {
    searchParams(user) { params =>
      search(params, user, searchPath)
    }
  }

  def organizationBehavior(organization: String, user: Option[UserState]): Route = {
      searchParams(user) { params =>
        search(
          params.copy(queryString = s"${params.queryString} AND organization:$organization"),
          user,
          organization
        )
    }
  }
}
