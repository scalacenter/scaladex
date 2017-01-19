package ch.epfl.scala.index
package server
package routes
package api

import data.DataPaths
import model.release._
import akka.http.scaladsl._
import model._
import server.Directives._
import StatusCodes._
import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.epfl.scala.index.data.github.GithubCredentials
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext

class PublishApi(paths: DataPaths, dataRepository: DataRepository, val github: Github)(
  implicit val system: ActorSystem,
  implicit val materializer: ActorMaterializer) {

  import system.dispatcher

  /*
   * extract a maven reference from path like
   * /com/github/scyks/playacl_2.11/0.8.0/playacl_2.11-0.8.0.pom =>
   * MavenReference("com.github.scyks", "playacl_2.11", "0.8.0")
   *
   * @param path the real publishing path
   * @return MavenReference
   */
  private def mavenPathExtractor(path: String): MavenReference = {

    val segments = path.split("/").toList
    val size = segments.size
    val takeFrom = if (segments.head.isEmpty) 1 else 0

    val artifactId = segments(size - 3)
    val version = segments(size - 2)
    val groupId = segments.slice(takeFrom, size - 3).mkString(".")

    MavenReference(groupId, artifactId, version)
  }

  import akka.pattern.ask

  import scala.concurrent.duration._

  implicit val timeout = Timeout(40.seconds)

  private val actor =
    system.actorOf(Props(classOf[impl.PublishActor], paths, dataRepository, system, materializer))

  def publishBehavior(path: String, created: DateTime, readme: Boolean, contributors: Boolean, info: Boolean, keywords: Iterable[String], test: Boolean, data: String, auth: (GithubCredentials, UserState)) = {
    auth match {
      case (credentials, userState) =>
        val publishData = impl.PublishData(
          path,
          created,
          data,
          credentials,
          userState,
          info,
          contributors,
          readme,
          keywords.toSet,
          test
        )

        complete((actor ? publishData).mapTo[(StatusCode, String)].map(s => s))
    }
  }

  def publishStatusBehavior(path: String) = {
    complete {

      /* check if the release already exists - sbt will handle HTTP-Status codes
           * NotFound -> allowed to write
           * OK -> only allowed if isSnapshot := true
           */
      dataRepository.maven(mavenPathExtractor(path)) map {
        case Some(release) => (OK, "release already exists")
        case None => (NotFound, "ok to publish")
      }
    }
  }

  val executionContext = implicitly[ExecutionContext]
}
