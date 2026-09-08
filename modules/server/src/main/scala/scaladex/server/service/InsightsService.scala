package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.service.SchedulerDatabase

import com.typesafe.scalalogging.LazyLogging

class InsightsService(database: SchedulerDatabase)(using ExecutionContext) extends LazyLogging:

  def updateAll(): Future[String] =
    val versionsF = updateScalaVersionInsights()
    val migrationF = updateScala3MigrationInsights()
    for
      versionCount <- versionsF
      _ <- migrationF
    yield s"Updated Scala version insights for $versionCount versions and Scala 3 migration status"

  private def updateScalaVersionInsights(): Future[Int] = for
    insights <- database.computeScalaVersionInsights()
    _ <- database.replaceScalaVersionInsights(insights)
  yield insights.size

  private def updateScala3MigrationInsights(): Future[Unit] = for
    migration <- database.computeScala3MigrationInsights()
    _ <- database.replaceScala3MigrationInsights(migration)
  yield ()
end InsightsService
