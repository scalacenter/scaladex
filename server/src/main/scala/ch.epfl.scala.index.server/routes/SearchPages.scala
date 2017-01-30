package ch.epfl.scala.index
package server
package routes

import views.search.html._

import model.misc.SearchParams

import com.softwaremill.session._, SessionDirectives._, SessionOptions._

import TwirlSupport._

import akka.http.scaladsl._
import model._
import server._
import server.Directives._
import Uri._

import java.util.UUID

class SearchPages(dataRepository: DataRepository, session: GithubUserSession) {
  import session._
  import dataRepository._

  private def search(params: SearchParams, userId: Option[UUID], uri: String) = {
    complete(
      for {
        (pagination, projects) <- find(params)
        keywords <- keywords(params)
        targetTypes <- targetTypes(params)
        scalaVersions <- scalaVersions(params)
        scalaJsVersions <- scalaJsVersions(params)
        scalaNativeVersions <- scalaNativeVersions(params)
      } yield {
        searchresult(
          params,
          uri,
          pagination,
          projects,
          getUser(userId).map(_.user),
          params.userRepos.nonEmpty,
          keywords,
          targetTypes,
          scalaVersions,
          scalaJsVersions,
          scalaNativeVersions
        )
      }
    )
  }

  def searchParams(userId: Option[UUID]): Directive1[SearchParams] =
    parameters(
      ('q ? "*",
       'page.as[Int] ? 1,
       'sort.?,
       'keywords.*,
       'targetTypes.*,
       'scalaVersions.*,
       'scalaJsVersions.*,
       'scalaNativeVersions.*,
       'you.?)).tmap {
      case (q,
            page,
            sort,
            keywords,
            targetTypes,
            scalaVersions,
            scalaJsVersions,
            scalaNativeVersions,
            you) =>
        val userRepos = you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set())
        SearchParams(
          q,
          page,
          sort,
          userRepos,
          keywords = keywords.toList,
          targetTypes = targetTypes.toList,
          scalaVersions = scalaVersions.toList,
          scalaJsVersions = scalaJsVersions.toList,
          scalaNativeVersions = scalaNativeVersions.toList
        )
    }

  private val searchPath = "search"

  val routes =
    get {
      path(searchPath) {
        optionalSession(refreshable, usingCookies) { userId =>
          searchParams(userId) { params =>
            search(params, userId, searchPath)
          }
        }
      } ~
        path(Segment) { organization =>
          optionalSession(refreshable, usingCookies) { userId =>
            searchParams(userId) { params =>
              search(
                params.copy(queryString = s"${params.queryString} AND organization:$organization"),
                userId,
                organization
              )
            }
          }
        }
    }
}
