package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._

class ProjectDependenciesUpdater(database: SchedulerDatabase)(implicit ec: ExecutionContext) {
  def updateAll(): Future[Unit] =
    for {
      projectWithDependencies <- database
        .computeProjectDependencies()
        .mapFailure(e =>
          new Exception(
            s"Failed to compute project dependencies because of ${e.getMessage}"
          )
        )
      _ <- database.deleteDependenciesOfMovedProject()
      _ <- database
        .insertProjectDependencies(projectWithDependencies)
        .mapFailure(e =>
          new Exception(
            s"Failed to insert project dependencies because of ${e.getMessage}"
          )
        )

    } yield ()
}
