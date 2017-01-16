package ch.epfl.scala.index
package server
package routes

import views.search.html._

import model.release.ScalaTarget
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
          getUser(userId).map(_.user),
          params.userRepos.nonEmpty,
          keywords,
          parsedTargets
        )
      }
    )
  }

  def searchParams(userId: Option[UUID]): Directive1[SearchParams] =
    parameters(('q ? "*", 'page.as[Int] ? 1, 'sort.?, 'keywords.*, 'targets.*, 'you.?)).tmap{
          case ( q      ,  page            ,  sort  ,  keywords  ,  targets  ,  you) =>

      val userRepos = you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set())
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

  val routes =
    get {
      path(searchPath) {
        optionalSession(refreshable, usingCookies) { userId =>
          searchParams(userId){ params =>
            search(params, userId, searchPath)
          }
        }
      } ~
        path(Segment) { organization =>
          optionalSession(refreshable, usingCookies) { userId =>
            searchParams(userId){ params =>
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
