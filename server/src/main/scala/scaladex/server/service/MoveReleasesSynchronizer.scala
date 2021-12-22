package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.utils.ScalaExtensions.TraversableOnceFutureExtension

class MoveReleasesSynchronizer(db: SchedulerDatabase)(implicit ec: ExecutionContext)
    extends Scheduler("move-releases-synchronizer", 5.minutes) {
  override def run(): Future[Unit] =
    for {
      allProjects <- db.getAllProjects()
      moved = allProjects.collect {
        case Project(oldRef, oldRepo, _, _ @GithubStatus.Moved(_, newRef), _, _) =>
          Project.Reference(oldRef, oldRepo) -> newRef
      }.toMap
      numberOfUpdated <- moved.map {
        case (oldRef, newRef) => db.findReleases(oldRef).flatMap(releases => db.updateReleases(releases, newRef))
      }.sequence
      _ = logger.info(
        s"${numberOfUpdated.sum} releases have been updated with the new organization/new repository names"
      )
    } yield ()

}
