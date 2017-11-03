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
    complete {
      val resultsF = find(params)
      val topicsF = topics(Some(params))
      val targetTypesF = targetTypes(Some(params))
      val scalaVersionsF = scalaVersions(Some(params))
      val scalaJsVersionsF = scalaJsVersions(Some(params))
      val scalaNativeVersionsF = scalaNativeVersions(Some(params))
      val sbtVersionsF = sbtVersions(Some(params))
      val queryIsTopicF = queryIsTopic(params.queryString)

      for {
        (pagination, projects) <- resultsF
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
          projects,
          getUser(userId).map(_.user),
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
        val userRepos =
          you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set())
        SearchParams(
          q.toLowerCase,
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

  val routes =
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
