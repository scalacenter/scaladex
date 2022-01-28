package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.HtmlFormat
import scaladex.core.model.Category
import scaladex.core.model.Env
import scaladex.core.model.MetaCategory
import scaladex.core.model.UserState
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.server.TwirlSupport._
import scaladex.view.explore.html.exploreAll

class ExplorePages(env: Env, searchEngine: SearchEngine)(implicit ec: ExecutionContext) {

  def route(user: Option[UserState]): Route =
    get {
      path("explore") {
        complete(exploreAllPage(user))
      }
    }

  private def exploreAllPage(user: Option[UserState]): Future[HtmlFormat.Appendable] =
    for {
      allCategories <- Category.all
        .map(c => searchEngine.getByCategory(c, 4).map(c -> _))
        .sequence
    } yield {
      val categories = allCategories.filter { case (_, projects) => projects.nonEmpty }.toMap
      val metaCategories = MetaCategory.all
        .map(m => m -> m.categories.flatMap(c => categories.get(c).map(c -> _)))
        .filter { case (_, categories) => categories.nonEmpty }
      exploreAll(env, metaCategories, user)
    }
}
