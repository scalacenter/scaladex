package scaladex.server.route

import scala.concurrent.ExecutionContext

import org.apache.pekko.http.scaladsl.model.Uri.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.*
import scaladex.core.model.*
import scaladex.core.model.search.*
import scaladex.core.service.SearchEngine
import scaladex.server.TwirlSupport.given
import scaladex.view.search.html.searchresult

class SearchPages(env: Env, searchEngine: SearchEngine)(
    using ExecutionContext
):
  def route(user: Option[UserState]): Route =
    get(
      concat(
        path("search")(
          searchParams(user)(params => paging(size = 20)(page => search(params, page, user, "search")))
        ),
        path(Segment)(organization =>
          searchParams(user) { params =>
            val paramsWithOrg = params.copy(queryString = s"${params.queryString} AND organization:$organization")
            paging(size = 20)(page => search(paramsWithOrg, page, user, s"organization/$organization"))
          }
        )
      )
    )

  private def searchParams(user: Option[UserState]): Directive1[SearchParams] =
    parameters(
      "q" ? "*",
      "sort".?,
      "topic".as[String].*,
      "language".as[String].*,
      "platform".as[String].*,
      "you".?,
      "contributingSearch".as[Boolean] ? false
    ).tmap {
      case (q, sortParam, topics, languages, platforms, you, contributingSearch) =>
        val userRepos = you.flatMap(_ => user.map(_.repos)).getOrElse(Set())
        val sorting = sortParam.flatMap(Sorting.byLabel.get).getOrElse(Sorting.Stars)
        SearchParams(
          q,
          sorting,
          userRepos,
          topics = topics.toSeq,
          languages = languages.flatMap(Language.parse).toSeq,
          platforms = platforms.flatMap(Platform.parse).toSeq,
          contributingSearch = contributingSearch
        )
    }

  private def search(params: SearchParams, page: PageParams, user: Option[UserState], uri: String) =
    complete {
      val resultsF = searchEngine.find(params, page)
      val topicsF = searchEngine.countByTopics(params, 50)
      val platformsF = searchEngine.countByPlatforms(params)
      val languagesF = searchEngine.countByLanguages(params)

      for
        Page(pagination, projects) <- resultsF
        topics <- topicsF
        languages <- languagesF
        platforms <- platformsF
      yield searchresult(
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
      end for
    }
end SearchPages
