package ch.epfl.scala.index
package server

import routes._
import routes.api._

import data.DataPaths
import data.elastic._

import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import server.Directives._

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

import java.lang.management.ManagementFactory

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

// reStart 8080 /home/gui/center/scaladex/contrib /home/gui/center/scaladex-data-jungle
object Server {
  def main(args: Array[String]): Unit = {

    val port =
      if (args.isEmpty) 8080
      else args.head.toInt

    val config = ConfigFactory.load().getConfig("org.scala_lang.index.server")
    val production = config.getBoolean("production")

    if (production) {
      val pid = ManagementFactory.getRuntimeMXBean().getName().split("@").head
      val pidFile = Paths.get("PID")
      Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
      sys.addShutdownHook {
        Files.delete(pidFile)
      }
    }

    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github
    val data = new DataRepository(github)
    val session = new GithubUserSession(config)

    val pathFromArgs =
      if (args.isEmpty) Nil
      else args.toList.tail


    class Routes(data: DataRepository, session: GithubUserSession, github: Github) {
      val userFacingRoutes =
        concat(
          new FrontPage(data, session).routes,

          redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
            concat(
              new ProjectPages(data, session).routes,
              new SearchPages(data, session).routes
            )
          }
        )

      val paths = DataPaths(pathFromArgs)

      val programmaticRoutes = concat(
        new PublishApi(paths, data, github).routes,
        new SearchApi(data).routes,
        Assets.routes,
        new Badges(data).routes,
        new OAuth2(github, session).routes
      )

      val routes = programmaticRoutes ~ userFacingRoutes
    }

    val routes = new Routes(data, session, github).routes

    println("waiting for elastic to start")
    blockUntilYellow()
    println("ready")

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 20.seconds)

    println(s"port: $port")

    ()
  }
}
