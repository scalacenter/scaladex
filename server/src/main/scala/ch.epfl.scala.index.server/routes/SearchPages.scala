package ch.epfl.scala.index
package server
package routes

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.scala.index.model.misc.Page
import ch.epfl.scala.index.model.misc.SearchParams
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.index.server.TwirlSupport._
import ch.epfl.scala.index.views.search.html.searchresult
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

class SearchPages(dataRepository: ESRepo, session: GithubUserSession)(implicit
    ec: ExecutionContext
) {
  import dataRepository._
  import session.implicits._

  private def search(
      params: SearchParams,
      user: Option[UserState],
      uri: String
  ) = {
    complete {
      val resultsF = findProjects(params)
      val topicsF = getTopics(params)
      val targetTypesF = getTargetTypes(params)
      val scalaVersionsF = getScalaVersions(params)
      val scalaJsVersionsF = getScalaJsVersions(params)
      val scalaNativeVersionsF = getScalaNativeVersions(params)
      val sbtVersionsF = getSbtVersions(params)

      for {
        Page(pagination, projects) <- resultsF
        topics <- topicsF
        targetTypes <- targetTypesF
        scalaVersions <- scalaVersionsF
        scalaJsVersions <- scalaJsVersionsF
        scalaNativeVersions <- scalaNativeVersionsF
        sbtVersions <- sbtVersionsF
      } yield {
        searchresult(
          params,
          uri,
          pagination,
          projects.toList,
          user.map(_.info),
          params.userRepos.nonEmpty,
          topics,
          targetTypes,
          scalaVersions,
          scalaJsVersions,
          scalaNativeVersions,
          sbtVersions
        )
      }
    }
  }

  private val searchPath = "search"

  val routes: Route =
    get(
      concat(
        path(searchPath)(
          optionalSession(refreshable, usingCookies) { userId =>
            val user = session.getUser(userId)
            searchParams(user)(params => search(params, user, searchPath))
          }
        ),
        path(Segment)(organization =>
          optionalSession(refreshable, usingCookies) { userId =>
            val user = session.getUser(userId)
            searchParams(user)(params =>
              search(
                params.copy(
                  queryString =
                    s"${params.queryString} AND organization:$organization"
                ),
                user,
                organization
              )
            )
          }
        )
      )
    )
}
