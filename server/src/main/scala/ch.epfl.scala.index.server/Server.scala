package ch.epfl.scala.index
package server

import routes._
import routes.api._
import data.DataPaths
import data.elastic._
import akka.http.scaladsl._
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
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

    val paths: DataPaths = DataPaths(pathFromArgs)

    def buildRoutes(data: DataRepository, session: GithubUserSession, github: Github, paths: DataPaths)
                   (implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContext) = {
      new Routes(session,
        new FrontPage(data, session),
        new ProjectPages(data, session),
        new SearchPages(data, session),
        new PublishApi(paths, data, github),
        new SearchApi(data),
        new OAuth2(github, session),
        new Badges(data)).routes
    }

    val routes = buildRoutes(data, session, github, paths)

    println("waiting for elastic to start")
    blockUntilYellow()
    println("ready")

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 20.seconds)

    println(s"port: $port")

    ()
  }
}
