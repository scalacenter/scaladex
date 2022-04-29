package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.GithubStatus
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._

class ArtifactsService(database: SchedulerDatabase)(implicit ec: ExecutionContext) extends LazyLogging {
  def moveAll(): Future[String] =
    for {
      projectStatuses <- database.getAllProjectsStatuses()
      moved = projectStatuses.collect { case (ref, GithubStatus.Moved(_, dest)) => ref -> dest }
      total <- moved
        .map {
          case (source, dest) =>
            database.getArtifacts(source).flatMap(artifacts => database.updateArtifacts(artifacts, dest))
        }
        .sequence
        .map(_.sum)
    } yield s"Moved $total artifacts"
}
