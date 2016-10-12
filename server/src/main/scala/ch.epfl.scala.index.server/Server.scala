package ch.epfl.scala.index
package server

import routes._

// import model._
// import model.misc._
// import data.project.ProjectForm

// import release._
// import data.github.GithubCredentials
import data.elastic._
// import api.Autocompletion

import akka.http.scaladsl._
// import model._
// import headers.{HttpCredentials, Referer}
// import server.Directives._
// import server.directives._
// import Uri._
// import StatusCodes._
// import TwirlSupport._

import com.softwaremill.session._
// import com.softwaremill.session.CsrfDirectives._
// import com.softwaremill.session.CsrfOptions._
// import com.softwaremill.session.SessionDirectives._
// import com.softwaremill.session.SessionOptions._

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

// import upickle.default.{write => uwrite}

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try
import java.util.UUID
import scala.collection.parallel.mutable.ParTrieMap
import java.lang.management.ManagementFactory

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object Server {
  def main(args: Array[String]): Unit = {

    val port = 
      if(args.isEmpty) 8080
      else args.head.toInt

    val config = ConfigFactory.load().getConfig("org.scala_lang.index.server")
    val production = config.getBoolean("production")

    if(production) {
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

    val sessionConfig = SessionConfig.default(config.getString("sesssion-secret"))

    implicit def serializer: SessionSerializer[UUID, String] =
      new SingleValueSessionSerializer(
          _.toString(),
          (id: String) => Try { UUID.fromString(id) }
      )
    implicit val sessionManager = new SessionManager[UUID](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UUID] {
      def log(msg: String) =
        if (msg.startsWith("Looking up token for selector")) () // borring
        else println(msg)
    }

    val github = new Github
    val api = new DataRepository(github)


    val users = ParTrieMap[UUID, UserState]()
    // def getUser(id: Option[UUID]): Option[UserState] = id.flatMap(users.get)

    val route = Assets.routes
        

    println("waiting for elastic to start")
    blockUntilYellow()
    println("ready")

    Await.result(Http().bindAndHandle(route, "0.0.0.0", port), 20.seconds)

    println(s"port: $port")


    ()
  }
}
