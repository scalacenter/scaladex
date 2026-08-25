package scaladex.server.service

import scala.util.control.NonFatal

import scaladex.core.model.Project
import scaladex.core.service.ProjectService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging

class DependencyUpdater(database: SchedulerDatabase, projectService: ProjectService) extends LazyLogging:

  def updateAll(): IO[String] =
    for status <- updateProjectDependencyTable()
    yield status

  def updateProjectDependencyTable(): IO[String] =
    for
      allProjects <- database.getAllProjects()
      _ = logger.info(s"Updating dependencies of ${allProjects.size} projects")
      _ <- allProjects.mapIO(updateDependencies)
    yield s"Updated dependencies of ${allProjects.size} projects"

  def updateDependencies(project: Project): IO[Unit] =
    val action =
      if project.githubStatus.isMoved then database.deleteProjectDependencies(project.reference).void
      else
        for
          header <- projectService.getHeader(project)
          dependencies <- header
            .map(h => database.computeProjectDependencies(project.reference, h.latestVersion))
            .getOrElse(IO.pure(Seq.empty))
          _ <- database.deleteProjectDependencies(project.reference)
          _ <- database.insertProjectDependencies(dependencies)
        yield ()
    action.redeem(
      {
        case NonFatal(cause) =>
          logger
            .error(s"Failed to update dependencies of ${project.reference} of status ${project.githubStatus}", cause)
        case fatal => throw fatal
      },
      identity
    )
  end updateDependencies
end DependencyUpdater
