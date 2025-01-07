package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher1
import org.apache.pekko.http.scaladsl.server.Route
import play.twirl.api.Html
import scaladex.core.model.Category
import scaladex.core.model.Env
import scaladex.core.model.Language
import scaladex.core.model.MetaCategory
import scaladex.core.model.Platform
import scaladex.core.model.Scala
import scaladex.core.model.UserState
import scaladex.core.model.search.AwesomeParams
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.Sorting
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions.*
import scaladex.server.TwirlSupport.given
import scaladex.view.awesome.html

class AwesomePages(env: Env, searchEngine: SearchEngine)(using ExecutionContext):

  private val categoryM: PathMatcher1[Category] = Segment.flatMap(Category.byLabel.get)

  def route(user: Option[UserState]): Route =
    get {
      path("awesome") {
        awesomeParams(params => complete(awesomeScala(user, params)))
      } ~
        path("awesome" / categoryM) { category =>
          awesomeParams(params => paging(size = 20)(page => complete(awesome(user, category, params, page))))
        }
    }

  private val awesomeParams: Directive1[AwesomeParams] =
    parameters(
      "language".repeated,
      "platform".repeated,
      "sort".?
    ).tmap {
      case (languageParams, platformParams, sortParam) =>
        val scalaVersions = languageParams.flatMap(Language.parse).collect { case v: Scala => v }.toSeq
        val platforms = platformParams.flatMap(Platform.parse).toSeq
        val sorting = sortParam.flatMap(Sorting.byLabel.get).getOrElse(Sorting.Stars)
        Tuple1(AwesomeParams(scalaVersions, platforms, sorting))
    }

  private def awesomeScala(user: Option[UserState], params: AwesomeParams): Future[Html] =
    val allByCategoriesF = Category.all
      .map(c =>
        searchEngine
          .find(c, params, PageParams(1, 4))
          .map(p => c -> p.items)
      )
      .sequence
    val languagesCountF = searchEngine.countByLanguages()
    val platformsCountF = searchEngine.countByPlatforms()
    for
      allByCategories <- allByCategoriesF
      languagesCount <- languagesCountF
      platformsCount <- platformsCountF
    yield
      val projectsByCategory = allByCategories.filter { case (_, projects) => projects.nonEmpty }.toMap
      val categoriesByMetaCategory = MetaCategory.all
        .map(m => m -> m.categories.filter(projectsByCategory.contains))
        .filter { case (_, categories) => categories.nonEmpty }
      val scalaVersions = languagesCount.collect { case (v: Scala, _) => v }
      val platforms = platformsCount.map { case (p, _) => p }
      html.awesomeScala(env, user, categoriesByMetaCategory, projectsByCategory, scalaVersions, platforms, params)
    end for
  end awesomeScala

  private def awesome(
      user: Option[UserState],
      category: Category,
      params: AwesomeParams,
      page: PageParams
  ): Future[Html] =
    val languagesF = searchEngine.countByLanguages(category, params)
    val platformsF = searchEngine.countByPlatforms(category, params)
    val projectsF = searchEngine.find(category, params, page)
    for
      projects <- projectsF
      languages <- languagesF
      platforms <- platformsF
    yield
      val scalaVersions = languages.collect { case (v: Scala, c) => (v, c) }
      html.awesomeCategory(env, user, category, projects, scalaVersions, platforms, params)
  end awesome
end AwesomePages
