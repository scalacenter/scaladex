package ch.epfl.scala.index
package server

import build.info.BuildInfo
import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.elastic._
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.util.PidLock
import ch.epfl.scala.index.server.TwirlSupport._
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.index.server.routes._
import ch.epfl.scala.index.server.routes.api._
import ch.epfl.scala.index.search.DataRepository
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._

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
    val data = DataRepository.open(BuildInfo.baseDirectory)

    val searchPages = new SearchPages(data, session)
    val userFacingRoutes = concat(
      new FrontPage(data, session).routes,
      redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
        concat(
          new ProjectPages(
            data,
            session,
            new GithubDownload(paths),
            paths
          ).routes,
          searchPages.routes
        )
      }
    )
    val programmaticRoutes = concat(
      PublishApi(paths, data).routes,
      new SearchApi(data, session).routes,
      Assets.routes,
      new Badges(data).routes,
      Oauth2(config, session).routes
    )

    def hasParent(parentClass: Class[_], ex: Throwable)(): Boolean = {
      var current = ex
      def check: Boolean = parentClass == current.getClass
      var found = check

      while (!found && current.getCause != null) {
        current = current.getCause
        found = check
      }

      found
    }

    import session.implicits._
    val exceptionHandler = ExceptionHandler {
      case ex: Exception =>
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

    val routes =
      handleExceptions(exceptionHandler) {
        concat(programmaticRoutes, userFacingRoutes)
      }

    log.info("waiting for elastic to start")
    data.waitUntilReady()
    log.info("ready")

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 20.seconds)

    log.info(s"port: $port")
    log.info("Application started")
  }
}
