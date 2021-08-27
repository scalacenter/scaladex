package ch.epfl.scala.index
package server

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.effect.IO
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.util.PidLock
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.index.server.routes._
import ch.epfl.scala.index.server.routes.api._
import ch.epfl.scala.services.storage.sql.SqlRepo
import ch.epfl.scala.utils.DoobieUtils
import org.slf4j.LoggerFactory

object Server {
  private val log = LoggerFactory.getLogger(getClass)
  val config: ServerConfig = ServerConfig.load()

  def main(args: Array[String]): Unit = {
    val port =
      if (args.isEmpty) 8080
      else args.head.toInt

    if (config.production) {
      PidLock.create("SERVER")
    }

    implicit val system: ActorSystem = ActorSystem("scaladex")
    import system.dispatcher

    val pathFromArgs =
      if (args.isEmpty) Nil
      else args.toList.tail

    val paths = DataPaths(pathFromArgs)
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
        InternalServerError,
        out
      )
    }
    val transactor = DoobieUtils.transactor(config.dbConf)
    transactor
      .use { xa =>
        val db = new SqlRepo(config.dbConf, xa)
        val programmaticRoutes = concat(
          PublishApi(paths, data, databaseApi = db).routes,
          new SearchApi(data, session).routes,
          Assets.routes,
          new Badges(data).routes,
          Oauth2(config, session).routes
        )
        val userFacingRoutes = concat(
          new FrontPage(data, session).routes,
          redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
            concat(
              new ProjectPages(
                data,
                db,
                session,
                new GithubDownload(paths),
                paths
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
        db.migrate().unsafeRunSync()
        Await.result(
          Http().bindAndHandle(routes, "0.0.0.0", port),
          20.seconds
        )
        IO.never
      }
      .unsafeRunSync()

    log.info(s"port: $port")
    log.info("Application started")
  }
}
