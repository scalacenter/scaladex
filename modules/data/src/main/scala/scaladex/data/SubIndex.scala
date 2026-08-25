package scaladex.data

import scala.io.Source
import scala.util.Using

import scaladex.core.model.Project
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.Storage
import scaladex.core.util.ScalaExtensions.*

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging

class SubIndex(filesystem: Storage, database: SchedulerDatabase) extends LazyLogging:
  def run(): IO[Unit] =
    val projectSelection =
      Using.resource(Source.fromResource("subindex.txt", getClass.getClassLoader)) { source =>
        source.getLines().map(Project.Reference.unsafe).toSet
      }

    for
      allProjects <- database.getAllProjects()
      _ = logger.info(s"Clearing all previous projects")
      _ = filesystem.clearProjects()
      selectedProjects = allProjects.filter(p => projectSelection.contains(p.reference))
      _ = logger.info(s"Inserting ${selectedProjects.size} projects")
      _ <- selectedProjects.mapIO(saveProject)
    yield ()
  end run

  private def saveProject(project: Project): IO[Unit] =
    val ref = project.reference
    logger.info(s"Saving $ref")
    val artifactsF = database.getAllProjectArtifacts(ref)
    val dependenciesF = database.getProjectDependencies(ref)
    for
      artifacts <- artifactsF
      dependencies <- dependenciesF
    yield filesystem.saveProject(project, artifacts, dependencies)
end SubIndex

object SubIndex:
  def run(filesystem: Storage, database: SchedulerDatabase): IO[Unit] =
    val subIndex = new SubIndex(filesystem, database)
    subIndex.run()
