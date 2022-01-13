package scaladex.data.init

import java.time.Instant

import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Project
import scaladex.core.service.LocalStorageApi
import scaladex.core.util.ScalaExtensions._
import scaladex.data.maven.PomsReader
import scaladex.data.meta.ArtifactConverter
import scaladex.infra.CoursierResolver
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.sql.SqlDatabase

class Init(
    paths: DataPaths,
    database: SqlDatabase,
    localStorage: LocalStorageApi
)(implicit val system: ActorSystem)
    extends LazyLogging {
  import system.dispatcher
  val converter = new ArtifactConverter(paths)

  def run(): Future[Unit] = {
    logger.info("Dropping tables")
    for {
      _ <- database.dropTables.unsafeToFuture()
      _ = logger.info("Creating tables")
      _ <- database.migrate.unsafeToFuture()
      _ = logger.info("Inserting all artifacts from local storage...")
      _ <- insertAllArtifacts()
      _ = logger.info("Inserting all data forms form local storage...")
      _ <- insertAllProjectSettings()
      _ = logger.info("Inserting all github infos form local storage...")
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

  private def insertAllArtifacts(): Future[Unit] = {
    val pomsReader = new PomsReader(new CoursierResolver)
    pomsReader
      .loadAll(paths)
      .flatMap {
        case (pom, localRepo, sha1) =>
          converter.convert(pom, localRepo, sha1)
      }
      .map {
        case (artifact, dependencies) =>
          database.insertArtifact(artifact, dependencies, Instant.now)
      }
      .sequence
      .map(_ => ())
  }

  private def insertAllProjectSettings(): Future[Unit] = {
    val allSettings = localStorage.getAllProjectSettings()
    def updateSettings(ref: Project.Reference): Future[Unit] =
      allSettings
        .get(ref)
        .map(database.updateProjectSettings(ref, _))
        .getOrElse(Future.successful(()))
    for {
      projectStatuses <- database.getAllProjectsStatuses()
      refToUpdate = projectStatuses.collect { case (ref, status) if !status.moved && !status.notFound => ref }
      _ <- refToUpdate.map(updateSettings).sequence
    } yield ()
  }
}

object Init {
  def run(dataPaths: DataPaths, database: SqlDatabase, localStorage: LocalStorageApi)(
      implicit sys: ActorSystem
  ): Future[Unit] = {
    val init = new Init(dataPaths, database, localStorage)
    init.run()
  }
}
