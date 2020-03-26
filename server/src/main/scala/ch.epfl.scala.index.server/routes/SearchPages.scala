package ch.epfl.scala.index
package server
package routes

import java.util.UUID

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.scala.index.model.misc.{Page, SearchParams}
import ch.epfl.scala.index.server.TwirlSupport._
import ch.epfl.scala.index.views.search.html.searchresult
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import scala.concurrent.ExecutionContext

class SearchPages(dataRepository: DataRepository,
                  session: GithubUserSession)(implicit ec: ExecutionContext) {
  import dataRepository._
  import session.implicits._

  private def search(params: SearchParams, userId: Option[UUID], uri: String) = {
    complete {
      val resultsF = findProjects(params)
      val topicsF = getTopics(params)
      val targetTypesF = getTargetTypes(params)
      val scalaVersionsF = getScalaVersions(params)
      val scalaJsVersionsF = getScalaJsVersions(params)
      val scalaNativeVersionsF = getScalaNativeVersions(params)
      val sbtVersionsF = getSbtVersions(params)
      val queryIsTopicF = isTopic(params.queryString)

      for {
        Page(pagination, projects) <- resultsF
        topics <- topicsF
        targetTypes <- targetTypesF
        scalaVersions <- scalaVersionsF
        scalaJsVersions <- scalaJsVersionsF
        scalaNativeVersions <- scalaNativeVersionsF
        sbtVersions <- sbtVersionsF
        queryIsTopic <- queryIsTopicF
      } yield {
        searchresult(
          params,
          uri,
          pagination,
          projects.toList,
          session.getUser(userId).map(_.user),
          params.userRepos.nonEmpty,
          topics,
          targetTypes,
          scalaVersions,
          scalaJsVersions,
          scalaNativeVersions,
          sbtVersions,
          queryIsTopic
        )
      }
    }
  }

  def searchParams(userId: Option[UUID]): Directive1[SearchParams] =
    parameters(
      ('q ? "*",
       'page.as[Int] ? 1,
       'sort.?,
       'topics.*,
       'targetTypes.*,
       'scalaVersions.*,
       'scalaJsVersions.*,
       'scalaNativeVersions.*,
       'sbtVersions.*,
       'you.?,
       'contributingSearch.as[Boolean] ? false)
    ).tmap {
      case (q,
            page,
            sort,
            topics,
            targetTypes,
            scalaVersions,
            scalaJsVersions,
            scalaNativeVersions,
            sbtVersions,
            you,
            contributingSearch) =>
        val userRepos = you
          .flatMap(_ => session.getUser(userId).map(_.repos))
          .getOrElse(Set())
        SearchParams(
          q,
          page,
          sort,
          userRepos,
          topics = topics.toList,
          targetTypes = targetTypes.toList,
          scalaVersions = scalaVersions.toList,
          scalaJsVersions = scalaJsVersions.toList,
          scalaNativeVersions = scalaNativeVersions.toList,
          sbtVersions = sbtVersions.toList,
          contributingSearch = contributingSearch
        )
    }

  private val searchPath = "search"

  val routes: Route =
    get(
      concat(
        path(searchPath)(
          optionalSession(refreshable, usingCookies)(
            userId =>
              searchParams(userId)(
                params => search(params, userId, searchPath)
            )
          )
        ),
        path(Segment)(
          organization =>
            optionalSession(refreshable, usingCookies)(
              userId =>
                searchParams(userId)(
                  params =>
                    search(
                      params.copy(
                        queryString =
                          s"${params.queryString} AND organization:$organization"
                      ),
                      userId,
                      organization
                  )
              )
          )
        )
      )
    )
}
