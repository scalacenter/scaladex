package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.server.Route
import play.twirl.api.Html
import scaladex.core.model.Category
import scaladex.core.model.Env
import scaladex.core.model.Language
import scaladex.core.model.MetaCategory
import scaladex.core.model.Platform
import scaladex.core.model.Scala
import scaladex.core.model.UserState
import scaladex.core.model.search.ExploreParams
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.Sorting
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.server.TwirlSupport._
import scaladex.view.explore.html

class ExplorePages(env: Env, searchEngine: SearchEngine)(implicit ec: ExecutionContext) {

  private val categoryM: PathMatcher1[Category] = Segment.flatMap(Category.byLabel.get)

  def route(user: Option[UserState]): Route =
    get {
      path("explore") {
        exploreParams(params => complete(exploreAll(user, params)))
      } ~
        path("explore" / categoryM) { category =>
          exploreParams(params => paging(size = 20)(page => complete(explore(user, category, params, page))))
        }
    }

  private val exploreParams: Directive1[ExploreParams] =
    parameters(
      "languages".repeated,
      "platforms".repeated,
      "sort".?
    ).tmap {
      case (languageParams, platformParams, sortParam) =>
        val scalaVersions = languageParams.flatMap(Language.fromLabel).collect { case v: Scala => v }.toSeq
        val platforms = platformParams.flatMap(Platform.fromLabel).toSeq
        val sorting = sortParam.flatMap(Sorting.byLabel.get).getOrElse(Sorting.Stars)
        Tuple1(ExploreParams(scalaVersions, platforms, sorting))
    }

  private def exploreAll(user: Option[UserState], params: ExploreParams): Future[Html] = {
    val allByCategoriesF = Category.all
      .map(c =>
        searchEngine
          .find(c, params, PageParams(1, 4))
          .map(p => c -> p.items)
      )
      .sequence
    val languagesCountF = searchEngine.countByLanguages()
    val platformsCountF = searchEngine.countByPlatforms()
    for {
      allByCategories <- allByCategoriesF
      languagesCount <- languagesCountF
      platformsCount <- platformsCountF
    } yield {
      val byCategories = allByCategories.filter { case (_, projects) => projects.nonEmpty }.toMap
      val byMetaCategories = MetaCategory.all
        .map(m => m -> m.categories.flatMap(c => byCategories.get(c).map(c -> _)))
        .filter { case (_, categories) => categories.nonEmpty }
      val scalaVersions = languagesCount.collect { case (v: Scala, _) => v }
      val platforms = platformsCount.map { case (p, _) => p }
      html.exploreAll(env, user, byMetaCategories, scalaVersions, platforms, params)
    }
  }

  private def explore(
      user: Option[UserState],
      category: Category,
      params: ExploreParams,
      page: PageParams
  ): Future[Html] = {
    val languagesF = searchEngine.countByLanguages(category, params)
    val platformsF = searchEngine.countByPlatforms(category, params)
    val projectsF = searchEngine.find(category, params, page)
    for {
      projects <- projectsF
      languages <- languagesF
      platforms <- platformsF
    } yield {
      val scalaVersions = languages.collect { case (v: Scala, c) => (v, c) }
      html.exploreCategory(env, user, category, projects, scalaVersions, platforms, params)
    }
  }
}
