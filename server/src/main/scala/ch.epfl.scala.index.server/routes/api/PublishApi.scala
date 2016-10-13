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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future

class PublishApi(dataRepository: DataRepository, github: Github)(implicit val system: ActorSystem, 
                                                 implicit val materializer: ActorMaterializer) {
  import system.dispatcher

  private val publishProcess = new impl.PublishProcess(dataRepository)

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

  val routes = 
    get {
      path("publish") {
        parameter('path) { path =>
          complete {

            /* check if the release already exists - sbt will handle HTTP-Status codes
             * 404 -> allowed to write
             * 200 -> only allowed if isSnapshot := true
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
            'keywords.as[String].*
        ) { (path, readme, contributors, info, keywords) =>
          entity(as[String]) { data =>
            extractCredentials { credentials =>
              authenticateBasicAsync(realm = "Scaladex Realm", githubAuthenticator(credentials)) { 
                case (credentials, userState) =>

                  val publishData = impl.PublishData(path, data, credentials, userState, info, contributors, readme, keywords.toSet)
                  complete {
                    if (publishData.isPom) {
                      publishProcess.writeFiles(publishData)
                    } else {
                      Future(Created)
                    }
                  }
              }
            }
          }
        }
      }
    }
}