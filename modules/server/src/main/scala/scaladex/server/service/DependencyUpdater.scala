package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Project
import scaladex.core.service.ProjectService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*

class DependencyUpdater(database: SchedulerDatabase, projectService: ProjectService)(using ExecutionContext)
    extends LazyLogging:

  def updateAll(): Future[String] =
    for status <- updateProjectDependencyTable()
    yield status

  def updateProjectDependencyTable(): Future[String] =
    for
      allProjects <- database.getAllProjects()
      _ = logger.info(s"Updating dependencies of ${allProjects.size} projects")
      _ <- allProjects.mapSync(updateDependencies)
    yield s"Updated dependencies of ${allProjects.size} projects"

  def updateDependencies(project: Project): Future[Unit] =
    val future =
      if project.githubStatus.isMoved then database.deleteProjectDependencies(project.reference).map(_ => ())
      else
        for
          header <- projectService.getHeader(project)
          dependencies <- header
            .map(h => database.computeProjectDependencies(project.reference, h.latestVersion))
            .getOrElse(Future.successful(Seq.empty))
          _ <- database.deleteProjectDependencies(project.reference)
          _ <- database.insertProjectDependencies(dependencies)
        yield ()
    future.recover {
      case NonFatal(cause) =>
        logger.error(s"Failed to update dependencies of ${project.reference} of status ${project.githubStatus}", cause)
    }
  end updateDependencies
end DependencyUpdater
