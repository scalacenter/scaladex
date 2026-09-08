package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.service.SchedulerDatabase

import com.typesafe.scalalogging.LazyLogging

class InsightsService(database: SchedulerDatabase)(using ExecutionContext) extends LazyLogging:

  def updateAll(): Future[String] =
    for
      insights <- database.computeScalaVersionInsights()
      _ <- database.deleteAllScalaVersionInsights()
      _ <- database.insertScalaVersionInsights(insights)
      migration <- database.computeScala3MigrationInsights()
      _ <- database.deleteAllScala3MigrationInsights()
      _ <- database.insertScala3MigrationInsights(migration)
    yield s"Updated Scala version insights for ${insights.size} versions and Scala 3 migration status"
end InsightsService
