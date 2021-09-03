package ch.epfl.scala.index.data

import java.nio.file.Path
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.sys.process.Process
import scala.util.{Failure, Success, Try, Using}
import akka.actor.ActorSystem
import cats.effect._
import ch.epfl.scala.index.data.bintray.BintrayDownloadPoms
import ch.epfl.scala.index.data.bintray.BintrayListPoms
import ch.epfl.scala.index.data.bintray.UpdateBintraySbtPlugins
import ch.epfl.scala.index.data.central.CentralMissing
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.cleanup.NonStandardLib
import ch.epfl.scala.index.data.elastic.SeedElasticSearch
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.{DownloadParentPoms, PomsReader, ReleaseModel}
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.data.util.PidLock
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.newModel.NewProject.{Organization, Repository}
import ch.epfl.scala.index.newModel.{NewProject, NewRelease}
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.services.storage.sql.SqlRepo
import ch.epfl.scala.utils.DoobieUtils
import com.typesafe.scalalogging.LazyLogging
import doobie.hikari._
import org.joda.time.DateTime
import ch.epfl.scala.utils.ScalaExtensions._
import ch.epfl.scala.utils.Timer


/**
 * This application manages indexed POMs.
 */
object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {
    try {
      run(args)
    } catch {
      case fatal: Throwable =>
        logger.error("fatal error", fatal)
        sys.exit(1)
    }
  }

  /**
   * Update data:
   *  - pull the latest data from the 'contrib' repository
   *  - download data from Bintray and update the ElasticSearch index
   *  - commit the new state of the 'index' repository
   *
   * @param args 4 arguments:
   *              - Name of a step to execute (or “all” to execute all the steps)
   *              - Path of the 'contrib' Git repository
   *              - Path of the 'index' Git repository
   *              - Path of the 'credentials' Git repository
   */
  def run(args: Array[String]): Unit = {
    val config = IndexConfig.load()
    val global = ExecutionContext.global

    if (config.env.isDevOrProd) {
      PidLock.create("DATA")
    }

    logger.info("input: " + args.toList.toString)

    val bintray: LocalPomRepository = LocalPomRepository.Bintray

    implicit val system: ActorSystem = ActorSystem()

    val dataPaths = config.dataPaths

    val steps = List(
      // List POMs of Bintray
      Step("list")({ () =>
        // TODO: should be located in a config file
        val versions = List("2.13", "2.12", "2.11", "2.10")

        BintrayListPoms.run(dataPaths, versions, NonStandardLib.load(dataPaths))
      }),
      // Download POMs from Bintray
      Step("download")(() => new BintrayDownloadPoms(dataPaths).run()),
      // Download parent POMs
      Step("parent")(() => new DownloadParentPoms(bintray, dataPaths).run()),
      // Download ivy.xml descriptors of sbt-plugins from Bintray
      // and Github information of the corresponding projects
      Step("sbt") { () =>
        UpdateBintraySbtPlugins.run(dataPaths)
      },
      // Find missing artifacts in maven-central
      Step("central")(() => new CentralMissing(dataPaths).run()),
      // Download additional information about projects from Github
      // This step is not viable anymore because of the Github rate limit
      // which is to low to update all the projects.
      // As an alternative, the sbt steps handles the Github updates of its own projects
      // The IndexingActor does it as well for the projects that are pushed by Maven.
      Step("github")(() => GithubDownload.run(dataPaths)),
      // Re-create the ElasticSearch index
      Step("elastic") { () =>
        import system.dispatcher
        val transactor: Resource[IO, HikariTransactor[IO]] =
          DoobieUtils.transactor(config.db)
        transactor
          .use { xa =>
            val db = new SqlRepo(config.db, xa)
            IO(
              Using.resource(ESRepo.open()) { esRepo =>

                  Timer.timeAndLog(
                    Await.result(
                      insertDataInEsAndDb(dataPaths, esRepo, db),
                      Duration.Inf
                    ))((duration, _) => logger.info(s"inserting data in elasticSearch and postgresql took ${duration.toMinutes} minutes"))
              }
            )
          }
          .unsafeRunSync()
      },
      // insert a specific org/repo with its releases and dependencies
      Step("insert"){ () =>

        val (org, repo) = args.toList.tail match {
          case s"$org/$repo" :: Nil => (Organization(org), Repository(repo))
          case res => throw new Exception(s"""expecting one argument "organization/repository" not ${res}""")
        }

        val transactor: Resource[IO, HikariTransactor[IO]] =
          DoobieUtils.transactor(config.db)
        transactor
          .use { xa =>
            val db = new SqlRepo(config.db, xa)
            IO(
              Await.result(insertOneProject(db, org, repo, dataPaths)(global, system), Duration.Inf)
            )
          }.unsafeRunSync()
      }
    )

    def updateClaims(): Unit = {
      val githubRepoExtractor = new GithubRepoExtractor(dataPaths)
      githubRepoExtractor.updateClaims()
    }

    def subIndex(): Unit = {
      SubIndex.generate(
        source = dataPaths.fullIndex,
        destination = dataPaths.subIndex
      )
    }

    val stepsToRun =
      args.headOption match {
        case Some("all") => steps
        case Some("updateClaims") =>
          List(Step("updateClaims")(() => updateClaims()))
        case Some("subIndex") =>
          List(Step("subIndex")(() => subIndex()))
        case Some(name) =>
          steps
            .find(_.name == name)
            .fold(
              sys.error(
                s"Unknown step: $name. Available steps are: ${steps.map(_.name).mkString(" ")}."
              )
            )(List(_))
        case None =>
          sys.error(
            s"No step to execute. Available steps are: ${steps.map(_.name).mkString(" ")}."
          )
      }

    if (config.env.isDevOrProd) {
      inPath(dataPaths.contrib) { sh =>
        logger.info("Pulling the latest data from the 'contrib' repository")
        sh.exec("git", "checkout", "master")
        sh.exec("git", "remote", "update")
        sh.exec("git", "pull", "origin", "master")
      }
    }

    logger.info("Executing steps")
    stepsToRun.foreach(_.run())

    system.terminate()
    ()
  }

  class Step(val name: String)(effect: () => Unit) {
    def run(): Unit = {
      logger.info(s"Starting $name")
      effect()
      logger.info(s"$name done")
    }
  }

  object Step {
    def apply(name: String)(effect: () => Unit): Step = new Step(name)(effect)
  }

  def inPath(path: Path)(f: Sh => Unit): Unit = f(new Sh(path))

  class Sh(path: Path) {
    def exec(args: String*): Unit = {
      val process = Process(args, path.toFile)
      val status = process.!
      if (status == 0) ()
      else
        sys.error(
          s"Command '${args.mkString(" ")}' exited with status $status"
        )
    }
  }

  private def insertDataInEsAndDb(
      dataPaths: DataPaths,
      esRepo: ESRepo,
      db: SqlRepo
  )(implicit
      sys: ActorSystem
  ): Future[Long] = {
    import sys.dispatcher
    val githubDownload = new GithubDownload(dataPaths)
    val seed = new SeedElasticSearch(esRepo)

    //convert data
    val projectConverter = new ProjectConvert(dataPaths, githubDownload)
    val (projects, releases, dependencies) =
      projectConverter.convertAll(PomsReader.loadAll(dataPaths), Map())

    logger.info("Start cleaning both ES and PostgreSQL")
    val cleaning = for {
//      _ <- seed.cleanIndexes()
      _ <- db.dropTables().unsafeToFuture()
      _ <- db.migrate().unsafeToFuture()
    } yield ()

    // insertData
    for {
      _ <- cleaning
      insertedProject <- projects.map(NewProject.from).map(p => db.insertProject(p).failWithTry.map((p, _))).sequence
      _ = logFailures(insertedProject, NewProject.text , "projects" )
      insertedReleases <- releases.map(NewRelease.from).map(p => db.insertReleases(Seq(p)).failWithTry.map((p, _))).sequence
      _ = logFailures(insertedReleases, NewRelease.text , "releases" )
      _ = logger.info("starting to insert all dependencies in the database")

      dependenciesInsertion <- db.insertDependencies(dependencies).failWithTry
      _ = if(dependenciesInsertion.isFailure) logger.info(s"failed to insert ${dependencies.size}")
      numberOfIndexedProjects <- db.countProjects()
      countGithubInfo <- db.countGithubInfo()
      countProjectUserDataForm <- db.countProjectDataForm()
      countReleases <- db.countReleases()
      countDependencies <- db.countDependencies()
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

  private def logFailures[A, B](res: Iterator[(A, Try[B])], tostring: A => String, table: String) = {
    val failures = res.collect{ case (a, Failure(exception)) =>
      logger.warn(s"failed to insert ${tostring(a)} because ${exception.getMessage}")
      a
      }
    logger.warn(s"${failures.size} insertion failed in $table")
  }


  private def insertOneProject(db: SqlRepo, org: Organization, repo: Repository, dataPaths: DataPaths)(implicit ec: ExecutionContext, sys: ActorSystem): Future[Unit] = {
    val githubDownload = new GithubDownload(dataPaths)
    val projectConverter = new ProjectConvert(dataPaths, githubDownload)
      val allPoms: Iterable[(ReleaseModel, LocalRepository, String)] = PomsReader.loadAll(dataPaths) // let's read everything. Didn't find a way to find the path of poms using a organization and repo
      val githubRepoExtractor = new GithubRepoExtractor(dataPaths) // the only way to get organization and repo from a releaseModel
      val githubRepo = GithubRepo(org.value, repo.value)

      val allInsertions = allPoms.filter { case (model, _, _) =>
        githubRepoExtractor(model).contains(githubRepo)
      }.flatMap {case (model, repository, sha1) =>
        projectConverter.convertOne(model, repository, sha1,
          DateTime.now(), // should be read from meta.json :s
          githubRepo, None)
      }.map { case (project, release, dependencies) =>
        println(s"release.maven = ${release.maven}")
        (for {
          _ <- db.insertOrUpdateProject(project).mapFailure(e => new Exception(s"Not able to insert ${project.reference} because ${e.getMessage}"))
          _ <- db.insertRelease(release).mapFailure(e => new Exception(s"Not able to insert ${release.maven} because ${e.getMessage}"))
          _ <- db.insertDependencies(dependencies.iterator).mapFailure(e => new Exception(s"Not able to insert dependencies for ${release.maven} because ${e.getMessage}"))
        } yield ()).failWithTry
      }.sequence
    // let log failures
    for {
      (sucess, failures) <- allInsertions.map(elm => elm.partition {_.isSuccess})
      _ = logger.info(s"${failures.size} failure during insertion")
    } yield sucess.map(_.get)
  }
}
