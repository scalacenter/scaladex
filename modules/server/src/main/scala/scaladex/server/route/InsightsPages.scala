package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.Env
import scaladex.core.model.InsightsGranularity
import scaladex.core.model.Scala
import scaladex.core.model.ScalaVersionInsight
import scaladex.core.model.UserState
import scaladex.core.model.Version
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport.given
import scaladex.view.html.insights

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import play.twirl.api.Html

class InsightsPages(env: Env, database: WebDatabase)(using ExecutionContext):

  def route(user: Option[UserState]): Route =
    get(path("insights")(complete(insightsPage(user))))

  private def majorVersion(insight: ScalaVersionInsight): Int = insight.language match
    case Scala(v: Version.SemanticLike) => v.major
    case _ => -1

  private def insightsPage(user: Option[UserState]): Future[Html] =
    for
      rawInsights <- database.getScalaVersionInsights()
      migration <- database.getScala3MigrationInsights()
    yield
      val sorted = rawInsights.sortBy(_.language)
      val (binaryCompat, minor) = sorted.partition(_.granularity == InsightsGranularity.BinaryCompat)
      val (minor2x, minor3x) = minor.partition(majorVersion(_) == 2)
      insights(env, user, binaryCompat, migration.sortBy(_.migrated), minor2x, minor3x)
end InsightsPages
