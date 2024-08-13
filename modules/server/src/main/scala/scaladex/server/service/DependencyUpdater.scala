package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Project
import scaladex.core.model.ProjectHeader
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._

class DependencyUpdater(database: SchedulerDatabase)(implicit ec: ExecutionContext) extends LazyLogging {

  def updateAll(): Future[String] =
    for {
      status <- updateProjectDependencyTable()
    } yield status

  def updateProjectDependencyTable(): Future[String] =
    for {
      allProjects <- database.getAllProjects()
      _ = logger.info(s"Updating dependencies of ${allProjects.size} projects")
      _ <- allProjects.mapSync(updateDependencies)
    } yield s"Updated dependencies of ${allProjects.size} projects"

  def updateDependencies(project: Project): Future[Unit] = {
    val future =
      if (project.githubStatus.isMoved)
        database.deleteProjectDependencies(project.reference).map(_ => ())
      else
        for {
          latestArtifacts <- database.getLatestArtifacts(project.reference, project.settings.preferStableVersion)
          header = ProjectHeader(
            project.reference,
            latestArtifacts,
            0,
            project.settings.defaultArtifact,
            project.settings.preferStableVersion
          )
          dependencies <- header
            .map(h => database.computeProjectDependencies(project.reference, h.latestVersion))
            .getOrElse(Future.successful(Seq.empty))
          _ <- database.deleteProjectDependencies(project.reference)
          _ <- database.insertProjectDependencies(dependencies)
        } yield ()
    future.recover {
      case NonFatal(cause) =>
        logger.error(s"Failed to update dependencies of ${project.reference} of status ${project.githubStatus}", cause)
    }
  }
}
