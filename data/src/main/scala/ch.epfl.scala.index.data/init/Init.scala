package ch.epfl.scala.index.data.init

import java.time.Instant

import scala.concurrent.Future

import akka.actor.ActorSystem
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.data.meta.ReleaseConverter
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Project
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.local.LocalStorageRepo
import scaladex.infra.storage.sql.SqlRepo

class Init(
    paths: DataPaths,
    db: SqlRepo
)(implicit val system: ActorSystem)
    extends LazyLogging {
  import system.dispatcher
  val converter = new ReleaseConverter(paths)
  val localStorage = new LocalStorageRepo(paths)

  def run(): Future[Unit] = {
    logger.info("Dropping tables")
    for {
      _ <- db.dropTables.unsafeToFuture()
      _ = logger.info("Creating tables")
      _ <- db.migrate.unsafeToFuture()
      _ = logger.info("Inserting all releases from local storage...")
      _ <- insertAllReleases()
      _ = logger.info("Inserting all data forms form local storage...")
      _ <- insertAllDataForms()
      _ = logger.info("Inserting all github infos form local storage...")
      // counting what have been inserted
      projectCount <- db.countProjects()
      dataFormCount <- db.countProjectDataForm()
      releaseCount <- db.countArtifacts()
      dependencyCount <- db.countDependencies()

    } yield {
      logger.info(s"$projectCount projects are inserted")
      logger.info(s"$dataFormCount data forms are inserted")
      logger.info(s"$releaseCount releases are inserted")
      logger.info(s"$dependencyCount dependencies are inserted")
    }
  }

  private def insertAllReleases(): Future[Unit] =
    PomsReader
      .loadAll(paths)
      .flatMap {
        case (pom, localRepo, sha1) =>
          converter.convert(pom, localRepo, sha1)
      }
      .map {
        case (release, dependencies) =>
          db.insertRelease(release, dependencies, Instant.now)
      }
      .sequence
      .map(_ => ())

  private def insertAllDataForms(): Future[Unit] = {
    val allDataForms = localStorage.allDataForms()
    def updateDataForm(ref: Project.Reference): Future[Unit] =
      allDataForms
        .get(ref)
        .map(db.updateProjectForm(ref, _))
        .getOrElse(Future.successful(()))
    for {
      projectRefs <- db.getAllProjectRef()
      _ <- projectRefs.map(updateDataForm).sequence
    } yield ()
  }
}

object Init {
  def run(dataPaths: DataPaths, db: SqlRepo)(implicit sys: ActorSystem): Future[Unit] = {
    val init = new Init(dataPaths, db)
    init.run()
  }
}
