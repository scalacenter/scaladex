package ch.epfl.scala.index
package server
package routes

import views.search.html._

import model.release.ScalaTarget

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
          parameters('q, 'page.as[Int] ? 1, 'sort.?, 'keywords.*, 'targets.*, 'you.?) { 
            (query, page, sorting, filterKeywords, filterTargets, you) =>
            
            val userRepos = you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set())

            val params = SearchParams(
              query,
              page,
              sorting,
              userRepos,
              keywords = filterKeywords.toList,
              targets = filterTargets.toList
            )
            
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
                  query,
                  "search",
                  sorting,
                  pagination,
                  projects,
                  getUser(userId).map(_.user),
                  you.isDefined,
                  keywords,
                  parsedTargets,
                  filterKeywords.toSet,
                  filterTargets.toSet
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
