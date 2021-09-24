package ch.epfl.scala.index
package server

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.effect.IO
import ch.epfl.scala.index.data.util.PidLock
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.index.server.routes._
import ch.epfl.scala.index.server.routes.api._
import ch.epfl.scala.services.storage.local.LocalStorageRepo
import ch.epfl.scala.services.storage.sql.SqlRepo
import ch.epfl.scala.utils.DoobieUtils
import org.slf4j.LoggerFactory

object Server {
  private val log = LoggerFactory.getLogger(getClass)
  val config: ServerConfig = ServerConfig.load()

  def main(args: Array[String]): Unit = {
    if (config.api.env.isDevOrProd) {
      PidLock.create("SERVER")
    }

    implicit val system: ActorSystem = ActorSystem("scaladex")
    import system.dispatcher

    val paths = config.dataPaths
    val session = GithubUserSession(config)

    // the DataRepository will not be closed until the end of the process,
    // because of the sbtResolver mode
    val data = ESRepo.open()

    val searchPages = new SearchPages(data, session)

    val exceptionHandler = ExceptionHandler { case ex: Exception =>
      import java.io.{PrintWriter, StringWriter}

      val sw = new StringWriter()
      val pw = new PrintWriter(sw)
      ex.printStackTrace(pw)

      val out = sw.toString

      log.error(out)

      complete(
        StatusCodes.InternalServerError,
        out
      )
    }

    val transactor = DoobieUtils.transactor(config.dbConf)
    transactor
      .use { xa =>
        val db = new SqlRepo(config.dbConf, xa)
        val localStorage = new LocalStorageRepo(config.dataPaths)
        val programmaticRoutes = concat(
          PublishApi(paths, data, databaseApi = db).routes,
          new SearchApi(data, session).routes,
          Assets.routes,
          new Badges(data).routes,
          Oauth2(config, session).routes
        )
        val userFacingRoutes = concat(
          new FrontPage(data, db, session).routes,
          redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
            concat(
              new ProjectPages(
                db,
                localStorage,
                session,
                paths,
                config.api.env
              ).routes,
              searchPages.routes
            )
          }
        )

        val routes =
          handleExceptions(exceptionHandler) {
            concat(programmaticRoutes, userFacingRoutes)
          }

        log.info("waiting for elastic to start")
        data.waitUntilReady()
        log.info("ready")

        // apply migrations to the database if any.
        db.migrate.unsafeRunSync()

        await(
          Http()
            .bindAndHandle(routes, config.api.endpoint, config.api.port)
            .andThen {
              case Failure(exception) =>
                log.error("Unable to start the server", exception)
                System.exit(1)
              case Success(binding) =>
                log.info(
                  s"Server started at http://${config.api.endpoint}:${config.api.port}"
                )
                sys.addShutdownHook {
                  log.info("Stopping server")
                  await(binding.terminate(hardDeadline = 10.seconds))
                }
            }
        )
        IO.never
      }
      .unsafeRunSync()
  }

  private def await[A](f: Future[A]) = Await.result(f, Duration.Inf)
}
