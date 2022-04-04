package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._

class ProjectDependenciesUpdater(database: SchedulerDatabase)(implicit ec: ExecutionContext) extends LazyLogging {

  def updateAll(): Future[Unit] =
    for {
      _ <- updateProjectDependencyTable()
      _ <- updateReleaseDependencyTable()
    } yield ()

  def updateProjectDependencyTable(): Future[Unit] =
    for {
      projectWithDependencies <- database
        .computeProjectDependencies()
        .mapFailure(e =>
          new Exception(
            s"Failed to compute project dependencies because of ${e.getMessage}"
          )
        )
      _ = logger.info(s"will try to insert ${projectWithDependencies.size} projectDependencies")
      _ <- database.deleteDependenciesOfMovedProject()
      _ <- database
        .insertProjectDependencies(projectWithDependencies)
        .mapFailure(e =>
          new Exception(
            s"Failed to insert project dependencies because of ${e.getMessage}"
          )
        )

    } yield ()

  def updateReleaseDependencyTable(): Future[Unit] =
    for {
      releaseDependencies <- database
        .computeReleaseDependencies()
        .mapFailure(e =>
          new Exception(
            s"Failed to compute release dependencies because of ${e.getMessage}"
          )
        )
      _ = logger.info(s"will try to insert ${releaseDependencies.size} releaseDependencies")
      _ <- releaseDependencies
        .grouped(10000)
        .map(releaseDependencies =>
          database
            .insertReleaseDependencies(releaseDependencies)
            .mapFailure(e =>
              new Exception(
                s"Failed to insert release dependencies because of ${e.getMessage}"
              )
            )
        )
        .sequence

    } yield ()
}
