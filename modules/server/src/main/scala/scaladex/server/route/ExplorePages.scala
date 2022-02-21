package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.HtmlFormat
import scaladex.core.model.Category
import scaladex.core.model.Env
import scaladex.core.model.Language
import scaladex.core.model.MetaCategory
import scaladex.core.model.Platform
import scaladex.core.model.Scala
import scaladex.core.model.UserState
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.Sorting
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.server.TwirlSupport._
import scaladex.view.explore.html.exploreAll

class ExplorePages(env: Env, searchEngine: SearchEngine)(implicit ec: ExecutionContext) {

  def route(user: Option[UserState]): Route =
    get {
      path("explore") {
        parameters("languages".repeated, "platforms".repeated) { (languageParams, platformParams) =>
          val scalaVersions = languageParams.flatMap(Language.fromLabel).collect { case v: Scala => v }.toSeq
          val platforms = platformParams.flatMap(Platform.fromLabel).toSeq
          complete(exploreAllPage(user, scalaVersions, platforms))
        }
      }
    }

  private def exploreAllPage(
      user: Option[UserState],
      selectedScalaVersions: Seq[Scala],
      selectedplatforms: Seq[Platform]
  ): Future[HtmlFormat.Appendable] = {
    val allByCategoriesF = Category.all
      .map(c =>
        searchEngine
          .getByCategory(c, selectedScalaVersions, selectedplatforms, Sorting.Stars, PageParams(1, 4))
          .map(p => c -> p.items)
      )
      .sequence
    val allLanguagesF = searchEngine.getAllLanguages()
    val allPlatformsF = searchEngine.getAllPlatforms()
    for {
      allByCategories <- allByCategoriesF
      allLanguages <- allLanguagesF
      allPlatforms <- allPlatformsF
    } yield {
      val byCategories = allByCategories.filter { case (_, projects) => projects.nonEmpty }.toMap
      val byMetaCategories = MetaCategory.all
        .map(m => m -> m.categories.flatMap(c => byCategories.get(c).map(c -> _)))
        .filter { case (_, categories) => categories.nonEmpty }
      val allScalaVersions = allLanguages.collect { case v: Scala => v }
      exploreAll(env, user, byMetaCategories, allScalaVersions, allPlatforms, selectedScalaVersions, selectedplatforms)
    }
  }
}
