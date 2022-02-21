package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import scaladex.core.model.GithubStatus
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._

class MovedArtifactsSynchronizer(database: SchedulerDatabase)(implicit ec: ExecutionContext)
    extends Scheduler("move-artifacts", 5.minutes) {
  override def run(): Future[Unit] =
    for {
      projectStatuses <- database.getAllProjectsStatuses()
      moved = projectStatuses.collect { case (ref, GithubStatus.Moved(_, newRef)) => ref -> newRef }.toMap
      numberOfUpdated <- moved.map {
        case (oldRef, newRef) =>
          database.getArtifacts(oldRef).flatMap(artifacts => database.updateArtifacts(artifacts, newRef))
      }.sequence
      _ = logger.info(
        s"${numberOfUpdated.sum} artifacts have been updated with the new organization/new repository names"
      )
    } yield ()

}
