package ch.epfl.scala.index
package server

import routes._
import routes.api._
import data.DataPaths
import data.util.PidLock
import data.elastic._
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import server.Directives._
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

object Server {
  def main(args: Array[String]): Unit = {

    val port =
      if (args.isEmpty) 8080
      else args.head.toInt

    val config = ConfigFactory.load().getConfig("org.scala_lang.index.server")
    val production = config.getBoolean("production")

    if (production) {
      PidLock.create("SERVER")
    }

    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val pathFromArgs =
      if (args.isEmpty) Nil
      else args.toList.tail

    val paths = DataPaths(pathFromArgs)

    val github = new Github
    val data = new DataRepository(github, paths)
    val session = new GithubUserSession(config)

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

    val programmaticRoutes = concat(
      new PublishApi(paths, data, github).routes,
      new SearchApi(data).routes,
      Assets.routes,
      new Badges(data).routes,
      new OAuth2(github, session).routes
    )

    val routes = programmaticRoutes ~ userFacingRoutes

    println("waiting for elastic to start")
    blockUntilYellow()
    println("ready")

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 20.seconds)

    println(s"port: $port")
    println("Application started")

    ()
  }
}
