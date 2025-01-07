package scaladex.data.init
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.service.Storage
import scaladex.core.util.ScalaExtensions.*
import scaladex.infra.SqlDatabase

import com.typesafe.scalalogging.LazyLogging

class Init(database: SqlDatabase, localStorage: Storage)(using ExecutionContext) extends LazyLogging:

  def run(): Future[Unit] =
    logger.info("Dropping tables")
    for
      _ <- database.dropTables.unsafeToFuture()
      _ = logger.info("Creating tables")
      _ <- database.migrate.unsafeToFuture()
      _ = logger.info("Inserting all projects from local storage...")
      projectIterator = localStorage.loadAllProjects()
      _ <- projectIterator.foreachSync {
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
  ): Future[Unit] =
    logger.info(s"Inserting project ${project.reference}")
    for
      _ <- database.insertProject(project)
      _ <- database.insertArtifacts(artifacts)
      _ <- database.insertDependencies(dependencies)
    yield ()
  end insertProject
end Init

object Init:
  def run(database: SqlDatabase, localStorage: Storage)(
      using ExecutionContext
  ): Future[Unit] =
    val init = new Init(database, localStorage)
    init.run()
