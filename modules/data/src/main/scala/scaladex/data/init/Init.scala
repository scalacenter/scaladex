package scaladex.data.init

import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.storage.local.LocalStorageRepo
import scaladex.infra.storage.sql.SqlDatabase

class Init(
    database: SqlDatabase,
    localStorage: LocalStorageRepo
)(implicit val system: ActorSystem)
    extends LazyLogging {
  import system.dispatcher

  def run(): Future[Unit] = {
    logger.info("Dropping tables")
    for {
      _ <- database.dropTables.unsafeToFuture()
      _ = logger.info("Creating tables")
      _ <- database.migrate.unsafeToFuture()
      _ = logger.info("Inserting all projects from local storage...")
      projectIterator = localStorage.getAllProjects()
      _ <- projectIterator.mapSync {
        case (project, artifacts, dependencies) => insertProject(project, artifacts, dependencies)
      }
      // counting what have been inserted
      projectCount <- database.countProjects()
      settingsCount <- database.countProjectSettings()
      artifactCount <- database.countArtifacts()
      dependencyCount <- database.countDependencies()
    } yield {
      logger.info(s"$projectCount projects are inserted")
      logger.info(s"$settingsCount project settings are inserted")
      logger.info(s"$artifactCount artifacts are inserted")
      logger.info(s"$dependencyCount dependencies are inserted")
    }
  }

  private def insertProject(
      project: Project,
      artifacts: Seq[Artifact],
      dependencies: Seq[ArtifactDependency]
  ): Future[Unit] = {
    logger.info(s"Inserting project ${project.reference}")
    for {
      _ <- database.insertProject(project)
      _ <- database.insertArtifacts(artifacts)
      _ <- database.insertDependencies(dependencies)
    } yield ()
  }
}

object Init {
  def run(database: SqlDatabase, localStorage: LocalStorageRepo)(
      implicit sys: ActorSystem
  ): Future[Unit] = {
    val init = new Init(database, localStorage)
    init.run()
  }
}
