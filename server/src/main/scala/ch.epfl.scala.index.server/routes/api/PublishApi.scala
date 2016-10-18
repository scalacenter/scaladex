package ch.epfl.scala.index
package server
package routes
package api

import model.release._
import data.github._

import akka.http.scaladsl._
import model._
import headers._
import server.Directives._
import server.directives._

import StatusCodes._
// import TwirlSupport._

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.Future

class PublishApi(dataRepository: DataRepository, github: Github)(
    implicit val system: ActorSystem, 
    implicit val materializer: ActorMaterializer) {
  
  import system.dispatcher

  /*
   * verifying a login to github
   * @param credentials the credentials
   * @return
   */
  private def githubAuthenticator(credentialsHeader: Option[HttpCredentials]): 
    Credentials => Future[Option[(GithubCredentials, UserState)]] = {

    case Credentials.Provided(username) => {
      credentialsHeader match {
        case Some(cred) => {
          val upw               = new String(new sun.misc.BASE64Decoder().decodeBuffer(cred.token()))
          val userPass          = upw.split(":")

          val token = userPass(1)
          val credentials = GithubCredentials(token)
          // todo - catch errors

          github.getUserStateWithToken(token).map(user => Some((credentials, user)))
        }
        case _ => Future.successful(None)
      }
    }
    case _ => Future.successful(None)
  }

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
    val size     = segments.size
    val takeFrom = if (segments.head.isEmpty) 1 else 0

    val artifactId = segments(size - 3)
    val version    = segments(size - 2)
    val groupId    = segments.slice(takeFrom, size - 3).mkString(".")

    MavenReference(groupId, artifactId, version)
  }

  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(20.seconds)
  private val actor = system.actorOf(Props(classOf[impl.PublishActor], dataRepository, system, materializer))

  val routes = 
    get {
      path("publish") {
        parameter('path) { path =>
          complete {

            /* check if the release already exists - sbt will handle HTTP-Status codes
             * NotFound -> allowed to write
             * OK -> only allowed if isSnapshot := true
             */
            dataRepository.maven(mavenPathExtractor(path)) map {
              case Some(release) => OK
              case None          => NotFound
            }
          }
        }
      }
    } ~
    put {
      path("publish") {
        parameters(
            'path,
            'readme.as[Boolean] ? true,
            'contributors.as[Boolean] ? true,
            'info.as[Boolean] ? true,
            'keywords.as[String].*,
            'test.as[Boolean] ? false
        ) { (path, readme, contributors, info, keywords, test) =>
          entity(as[String]) { data =>
            extractCredentials { credentials =>
              authenticateBasicAsync(realm = "Scaladex Realm", githubAuthenticator(credentials)) { 
                case (credentials, userState) =>

                  val publishData = impl.PublishData(
                      path, data, 
                      credentials, userState,
                      info, contributors, readme, keywords.toSet, test
                    )

                  complete((actor ? publishData).mapTo[Unit].map(_ => ""))
              }
            }
          }
        }
      }
    }
}