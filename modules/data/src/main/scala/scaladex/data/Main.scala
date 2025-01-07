package scaladex.data

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.sys.process.Process

import scaladex.core.util.ScalaExtensions.*
import scaladex.core.util.TimeUtils
import scaladex.data.init.Init
import scaladex.data.util.PidLock
import scaladex.infra.DataPaths
import scaladex.infra.FilesystemStorage
import scaladex.infra.SqlDatabase
import scaladex.infra.sql.DoobieUtils

import cats.effect.*
import com.typesafe.scalalogging.LazyLogging

/** This application manages indexed POMs.
  */
object Main extends LazyLogging:

  def main(args: Array[String]): Unit =
    try run(args)
    catch
      case fatal: Throwable =>
        logger.error("fatal error", fatal)
        sys.exit(1)

  /**   - subIndex: dump the database to JSON files under scaladex-index or scaladex-small-index
    *   - init: load the JSON files to the database
    *
    * @param args:
    *   "subIndex" or "init"
    */
  def run(args: Array[String]): Unit =
    val config = IndexConfig.load()

    if config.env.isDevOrProd then PidLock.create("DATA")

    logger.info("input: " + args.toList.toString)

    given ec: ExecutionContext = ExecutionContext.global

    val dataPaths = DataPaths.from(config.filesystem)
    val localStorage = FilesystemStorage(config.filesystem)
    val datasource = DoobieUtils.getHikariDataSource(config.database)

    def usingDatabase(f: SqlDatabase => Future[Unit]): Unit =
      given ContextShift[IO] = IO.contextShift(ec)
      DoobieUtils
        .transactor(datasource)
        .use { xa =>
          val database = new SqlDatabase(datasource, xa)
          IO.fromFuture(IO(f(database)))
        }
        .unsafeRunSync()
    end usingDatabase

    def init(): Unit =
      usingDatabase(database => Init.run(database, localStorage))

    def subIndex(): Unit =
      val storage = FilesystemStorage(config.filesystem)
      usingDatabase(database => SubIndex.run(storage, database))

    val steps = Map(
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

    if config.env.isDevOrProd then
      val shell = new Shell(dataPaths.contrib)
      logger.info("Pulling the latest data from the 'contrib' repository")
      shell.exec("git", "checkout", "master")
      shell.exec("git", "remote", "update")
      shell.exec("git", "pull", "origin", "master")

    logger.info(s"Executing $name")
    val (_, duration) = TimeUtils.measure(run())
    logger.info(s"$name done in ${duration.prettyPrint}")
  end run
end Main

class Shell(path: Path):
  def exec(args: String*): Unit =
    val process = Process(args, path.toFile)
    val status = process.!
    if status == 0 then ()
    else
      sys.error(
        s"Command '${args.mkString(" ")}' exited with status $status"
      )
