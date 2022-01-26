package scaladex.data

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.sys.process.Process

import akka.actor.ActorSystem
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import doobie.hikari._
import scaladex.core.util.ScalaExtensions._
import scaladex.core.util.TimerUtils
import scaladex.data.central.CentralMissing
import scaladex.data.init.Init
import scaladex.data.util.PidLock
import scaladex.infra.DataPaths
import scaladex.infra.FilesystemStorage
import scaladex.infra.SqlDatabase
import scaladex.infra.sql.DoobieUtils

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
   * @param args: "central" or "init"
   */
  def run(args: Array[String]): Unit = {
    val config = IndexConfig.load()

    if (config.env.isDevOrProd) {
      PidLock.create("DATA")
    }

    logger.info("input: " + args.toList.toString)

    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher

    val dataPaths = DataPaths.from(config.filesystem)
    val localStorage = FilesystemStorage(config.filesystem)

    def usingDatabase(f: SqlDatabase => Future[Unit]): Unit = {
      implicit val cs = IO.contextShift(system.dispatcher)
      val transactor: Resource[IO, HikariTransactor[IO]] =
        DoobieUtils.transactor(config.database)
      transactor
        .use { xa =>
          val database = new SqlDatabase(config.database, xa)
          IO.fromFuture(IO(f(database)))
        }
        .unsafeRunSync()
    }

    def init(): Unit =
      usingDatabase(database => Init.run(database, localStorage))

    def subIndex(): Unit = {
      val subFilesystem = FilesystemStorage(config.filesystem)
      usingDatabase(database => SubIndex.run(subFilesystem, database))
    }

    val steps = Map(
      // Find missing artifacts in maven-central
      "central" -> { () => new CentralMissing(dataPaths).run() },
      // Populate the database with poms and data from an index repo:
      // scaladex-small-index or scaladex-index
      "init" -> { () => init() },
      "subIndex" -> { () => subIndex() }
    )

    val name = args.headOption
      .getOrElse(
        sys.error(s"No step to execute. Available steps are: ${steps.keys.mkString(", ")}.")
      )

    val run = steps.getOrElse(
      name,
      sys.error(s"Unknown step: $name. Available steps are: ${steps.keys.mkString(", ")}.")
    )

    if (config.env.isDevOrProd) {
      val shell = new Shell(dataPaths.contrib)
      logger.info("Pulling the latest data from the 'contrib' repository")
      shell.exec("git", "checkout", "master")
      shell.exec("git", "remote", "update")
      shell.exec("git", "pull", "origin", "master")
    }

    logger.info(s"Executing $name")
    val (_, duration) = TimerUtils.measure(run())
    logger.info(s"$name done in ${duration.prettyPrint}")
    system.terminate()
  }
}

class Shell(path: Path) {
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
