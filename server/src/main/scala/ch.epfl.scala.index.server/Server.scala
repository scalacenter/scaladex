package ch.epfl.scala.index
package server

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
import ch.epfl.scala.services.WebDatabase
import ch.epfl.scala.services.storage.local.LocalStorageRepo
import ch.epfl.scala.services.storage.sql.SqlRepo
import ch.epfl.scala.utils.DoobieUtils
import org.slf4j.LoggerFactory
import scaladex.server.service.SchedulerService

object Server {
  private val log = LoggerFactory.getLogger(getClass)
  val config: ServerConfig = ServerConfig.load()

  def main(args: Array[String]): Unit = {
    if (config.api.env.isDevOrProd) {
      PidLock.create("SERVER")
    }
    implicit val system: ActorSystem = ActorSystem("scaladex")
    import system.dispatcher

    // the DataRepository will not be closed until the end of the process,
    // because of the sbtResolver mode
    val data = ESRepo.open()

    val resources =
      for {
        webPool <- DoobieUtils.transactor(config.dbConf)
        schedulerPool <- DoobieUtils.transactor(config.dbConf)
      } yield (webPool, schedulerPool)

    resources
      .use {
        case (webPool, schedulerPool) =>
          val webDb = new SqlRepo(config.dbConf, webPool)
          val schedulerDb = new SqlRepo(config.dbConf, schedulerPool)
          val schedulerService = new SchedulerService(schedulerDb)
          for {
            _ <- init(webDb, schedulerService, data)
            routes = configureRoutes(data, webDb, schedulerService)
            _ <- IO(
              Http()
                .bindAndHandle(routes, config.api.endpoint, config.api.port)
                .andThen {
                  case Failure(exception) =>
                    log.error("Unable to start the server", exception)
                    System.exit(1)
                  case Success(binding) =>
                    log.info(s"Server started at http://${config.api.endpoint}:${config.api.port}")
                    sys.addShutdownHook {
                      log.info("Stopping server")
                      binding.terminate(hardDeadline = 10.seconds)
                    }
                }
            )
            _ <- IO.never
          } yield ()
      }
      .unsafeRunSync()

  }

  private def init(db: SqlRepo, scheduler: SchedulerService, esRepo: ESRepo): IO[Unit] = {
    log.info("applying flyway migration to db")
    for {
      _ <- db.migrate
      _ = log.info("wait for ElasticSearch")
      _ <- IO(esRepo.waitUntilReady())
      _ = log.info("starting the scheduler")
      _ <- IO(scheduler.startAll())
    } yield ()
  }
  private def configureRoutes(esRepo: ESRepo, webDb: WebDatabase, schedulerService: SchedulerService)(
      implicit actor: ActorSystem
  ): Route = {
    import actor.dispatcher
    val paths = config.dataPaths
    val session = GithubUserSession(config)
    val searchPages = new SearchPages(esRepo, session)

    val localStorage = new LocalStorageRepo(paths)
    val programmaticRoutes = concat(
      PublishApi(paths, esRepo, webDb).routes,
      new SearchApi(esRepo, webDb, session).routes,
      Assets.routes,
      new Badges(webDb).routes,
      Oauth2(config, session).routes
    )
    val userFacingRoutes = concat(
      new FrontPage(esRepo, webDb, session).routes,
      new AdminPages(schedulerService, session).routes,
      redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
        concat(
          new ProjectPages(webDb, localStorage, session, paths, config.api.env).routes,
          searchPages.routes
        )
      }
    )
    val exceptionHandler = ExceptionHandler {
      case ex: Exception =>
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
    handleExceptions(exceptionHandler) {
      concat(programmaticRoutes, userFacingRoutes)
    }
  }
}
