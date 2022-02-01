package scaladex.server.route

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.core.model.search.Page
import scaladex.core.model.search.SearchParams
import scaladex.core.service.SearchEngine
import scaladex.server.TwirlSupport._
import scaladex.view.search.html.searchresult

class SearchPages(env: Env, searchEngine: SearchEngine)(
    implicit ec: ExecutionContext
) {
  def route(user: Option[UserState]): Route =
    get(
      concat(
        path("search")(
          searchParams(user)(params => search(params, user, "search"))
        ),
        path(Segment)(organization =>
          searchParams(user) { params =>
            val paramsWithOrg = params.copy(queryString = s"${params.queryString} AND organization:$organization")
            search(paramsWithOrg, user, s"organization/$organization")
          }
        )
      )
    )

  private def search(params: SearchParams, user: Option[UserState], uri: String) =
    complete {
      val resultsF = searchEngine.find(params)
      val topicsF = searchEngine.countByTopics(params, 50)
      val platformsF = searchEngine.countByPlatforms(params, 12)
      val languagesF = searchEngine.countByLanguages(params, 10)

      for {
        Page(pagination, projects) <- resultsF
        topics <- topicsF
        languages <- languagesF
        platforms <- platformsF
      } yield searchresult(
        env,
        params,
        uri,
        pagination,
        projects,
        user,
        params.userRepos.nonEmpty,
        topics,
        languages,
        platforms
      )
    }
}
