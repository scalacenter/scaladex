package ch.epfl.scala.index.data

import java.nio.file.Path
import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.sys.process.Process
import scala.util.Using

import akka.actor.ActorSystem
import cats.effect._
import ch.epfl.scala.index.data.bintray.BintrayDownloadPoms
import ch.epfl.scala.index.data.bintray.BintrayListPoms
import ch.epfl.scala.index.data.bintray.UpdateBintraySbtPlugins
import ch.epfl.scala.index.data.central.CentralMissing
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.cleanup.NonStandardLib
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.DownloadParentPoms
import ch.epfl.scala.index.data.seed.Seed
import ch.epfl.scala.index.data.util.PidLock
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.services.storage.LocalPomRepository
import ch.epfl.scala.services.storage.sql.SqlRepo
import ch.epfl.scala.utils.DoobieUtils
import ch.epfl.scala.utils.TimerUtils
import com.typesafe.scalalogging.LazyLogging
import doobie.hikari._

/**
 * This application manages indexed POMs.
 */
object Main extends LazyLogging {

  def main(args: Array[String]): Unit =
    try run(args)
    catch {
      case fatal: Throwable =>
        logger.error("fatal error", fatal)
        sys.exit(1)
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

    if (config.env.isDevOrProd) {
      PidLock.create("DATA")
    }

    logger.info("input: " + args.toList.toString)

    val bintray: LocalPomRepository = LocalPomRepository.Bintray

    implicit val system: ActorSystem = ActorSystem()

    val dataPaths = config.dataPaths

    val steps = List(
      // List POMs of Bintray
      Step("list") { () =>
        // TODO: should be located in a config file
        val versions = List("2.13", "2.12", "2.11", "2.10")

        BintrayListPoms.run(dataPaths, versions, NonStandardLib.load(dataPaths))
      },
      // Download POMs from Bintray
      Step("download")(() => new BintrayDownloadPoms(dataPaths).run()),
      // Download parent POMs
      Step("parent")(() => new DownloadParentPoms(bintray, dataPaths).run()),
      // Download ivy.xml descriptors of sbt-plugins from Bintray
      // and Github information of the corresponding projects
      Step("sbt")(() => UpdateBintraySbtPlugins.run(dataPaths)),
      // Find missing artifacts in maven-central
      Step("central")(() => new CentralMissing(dataPaths).run()),
      // Download additional information about projects from Github
      // This step is not viable anymore because of the Github rate limit
      // which is to low to update all the projects.
      // As an alternative, the sbt steps handles the Github updates of its own projects
      // The IndexingActor does it as well for the projects that are pushed by Maven.
      Step("github")(() => GithubDownload.run(dataPaths)),
      // Re-create the ElasticSearch index
      Step("seed") { () =>
        import system.dispatcher
        val transactor: Resource[IO, HikariTransactor[IO]] =
          DoobieUtils.transactor(config.db)
        transactor
          .use { xa =>
            val db = new SqlRepo(config.db, xa)
            IO(
              Using.resource(ESRepo.open()) { esRepo =>
                Await.result(
                  Seed.run(dataPaths, esRepo, db),
                  Duration.Inf
                )
              }
            )
          }
          .unsafeRunSync()
      }
    )

    def updateClaims(): Unit = {
      val githubRepoExtractor = new GithubRepoExtractor(dataPaths)
      githubRepoExtractor.updateClaims()
    }

    def subIndex(): Unit =
      SubIndex.generate(
        source = dataPaths.fullIndex,
        destination = dataPaths.subIndex
      )

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
      val start = Instant.now()
      logger.info(s"Starting $name at ${start}")
      effect()
      val duration = TimerUtils.toFiniteDuration(start, Instant.now())
      logger.info(s"$name done in ${duration.toMinutes} minutes")
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
}
