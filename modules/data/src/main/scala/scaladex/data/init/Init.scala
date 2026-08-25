package scaladex.data.init
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.service.Storage
import scaladex.core.util.ScalaExtensions.*
import scaladex.infra.SqlDatabase

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.Flyway

class Init(flyway: Flyway, database: SqlDatabase, localStorage: Storage) extends LazyLogging:

  def run(): IO[Unit] =
    logger.info("Dropping tables")
    for
      _ <- IO(flyway.clean())
      _ = logger.info("Creating tables")
      _ <- IO(flyway.migrate())
      _ = logger.info("Inserting all projects from local storage...")
      projectIterator = localStorage.loadAllProjects()
      _ <- projectIterator.foreachIO {
        case (project, artifacts, dependencies) => insertProject(project, artifacts, dependencies)
      }
      // counting what have been inserted
      projectCount <- database.countProjects()
      settingsCount <- database.countProjectSettings()
      artifactCount <- database.countArtifacts()
      dependencyCount <- database.countDependencies()
    yield
      logger.info(s"$projectCount projects are inserted")
      logger.info(s"$settingsCount project settings are inserted")
      logger.info(s"$artifactCount artifacts are inserted")
      logger.info(s"$dependencyCount dependencies are inserted")
    end for
  end run

  private def insertProject(
      project: Project,
      artifacts: Seq[Artifact],
      dependencies: Seq[ArtifactDependency]
  ): IO[Unit] =
    logger.info(s"Inserting project ${project.reference}")
    for
      _ <- database.insertProject(project)
      _ <- database.insertArtifacts(artifacts)
      _ <- database.insertDependencies(dependencies)
    yield ()
  end insertProject
end Init

object Init:
  def run(flyway: Flyway, database: SqlDatabase, localStorage: Storage): IO[Unit] =
    val init = new Init(flyway, database, localStorage)
    init.run()
