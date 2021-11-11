package ch.epfl.scala.index.data.init

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try

import akka.actor.ActorSystem
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.sql.SqlRepo
import ch.epfl.scala.utils.ScalaExtensions._
import com.sksamuel.elastic4s.requests.bulk.BulkResponseItem
import com.typesafe.scalalogging.LazyLogging

class Init(
    dataPaths: DataPaths,
    esRepo: ESRepo,
    db: SqlRepo
)(implicit val system: ActorSystem)
    extends LazyLogging {
  import system.dispatcher

  def run(): Future[Long] = {
    val githubDownload = new GithubDownload(dataPaths)

    // convert data
    val projectConverter = new ProjectConvert(dataPaths, githubDownload)
    val (projects, releases, dependencies) =
      projectConverter.convertAll(PomsReader.loadAll(dataPaths), Map())

    logger.info("Start cleaning both ES and PostgreSQL")
    val cleaning = for {
      _ <- cleanIndexes()
      _ <- db.dropTables.unsafeToFuture()
      _ <- db.migrate.unsafeToFuture()
    } yield ()

    // insertData
    for {
      _ <- cleaning
      _ = logger.info("inserting projects to database")
      insertedAnProject <- db.insertProjectsWithFailures(projects.map(NewProject.from))
      _ = logFailures(
        insertedAnProject,
        (p: NewProject) => p.reference.toString,
        "projects"
      )
      _ = logger.info("inserting releases to database")
      insertedReleases <- db.insertReleasesWithFailures(
        releases.map(NewRelease.from)
      )
      _ = logFailures(
        insertedReleases,
        (r: NewRelease) => r.maven.name,
        "releases"
      )
      dependenciesInsertion <- db
        .insertDependencies(dependencies)
        .failWithTry
      _ = if (dependenciesInsertion.isFailure)
        logger.info(s"failed to insert ${dependencies.size} into dependencies")
      // counting what have been inserted
      numberOfIndexedProjects <- db.countProjects()
      countGithubInfo <- db.countGithubInfo()
      countProjectUserDataForm <- db.countProjectDataForm()
      countReleases <- db.countReleases()
      countDependencies <- db.countDependencies()
      // inserting in ES
      _ <- insertES(projects, releases)
    } yield {
      logger.info(s"$numberOfIndexedProjects projects have been indexed")
      logger.info(s"$countGithubInfo countGithubInfo have been indexed")
      logger.info(
        s"$countProjectUserDataForm countProjectUserDataForm have been indexed"
      )
      logger.info(s"$countReleases release have been indexed")
      logger.info(s"$countDependencies dependencies have been indexed")
      numberOfIndexedProjects
    }
  }

  def cleanIndexes(): Future[Unit] =
    for {
      _ <- esRepo.deleteAll()
      _ = logger.info("creating index")
      _ <- esRepo.create()
    } yield ()

  def insertES(projects: Seq[Project], releases: Seq[Release]): Future[Unit] = {
    val projectF = esRepo.insertProjects(projects)
    val releasesF = esRepo.insertReleases(releases)
    for {
      projectResult <- projectF
      releasesResult <- releasesF
    } yield {
      logES(projectResult, "projects")
      logES(releasesResult, "releases")
    }
  }
  private def logES(res: Seq[BulkResponseItem], name: String): Unit = {
    val (resFailures, resSuccess) = res.partition(_.status >= 300)
    if (resFailures.nonEmpty) {
      resFailures.foreach(
        _.error.foreach(error => logger.error(error.reason))
      )
    }
    logger.info(
      s"${resSuccess.size} $name have been inserted into ElasticSearch"
    )
  }

  private def logFailures[A, B](res: Seq[(A, Try[B])], toString: A => String, table: String): Unit = {
    val failures = res.collect {
      case (a, Failure(exception)) =>
        logger.warn(
          s"failed to insert ${toString(a)} because ${exception.getMessage}"
        )
        a
    }
    if (failures.nonEmpty)
      logger.warn(s"${failures.size} insertion failed in table $table")
    else ()
  }
}

object Init {
  def run(dataPaths: DataPaths, esRepo: ESRepo, db: SqlRepo)(implicit sys: ActorSystem): Future[Long] = {
    val init = new Init(dataPaths, esRepo, db)
    init.run()
  }
}
