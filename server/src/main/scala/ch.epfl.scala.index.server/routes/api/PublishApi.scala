package ch.epfl.scala.index
package server
package routes
package api

import java.util.Base64

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.util.Timeout
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.search.ESRepo
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

class PublishApi(
    paths: DataPaths,
    dataRepository: ESRepo,
    github: Github
)(implicit system: ActorSystem) {

  private val log = LoggerFactory.getLogger(getClass)

  import system.dispatcher

  /*
   * verifying a login to github
   * @param credentials the credentials
   * @return
   */
  private def githubAuthenticator(
      credentialsHeader: Option[HttpCredentials]
  ): Credentials => Future[Option[(data.github.Credentials, UserState)]] = {

    case Credentials.Provided(username) => {
      credentialsHeader match {
        case Some(cred) => {
          val upw = new String(
            Base64.getDecoder.decode(cred.token())
          )
          val userPass = upw.split(":")

          val token = userPass(1)
          val credentials = data.github.Credentials(token)
          // todo - catch errors

          githubCredentialsCache.get(token) match {
            case res @ Some(_) => {
              Future.successful(res)
            }
            case _ => {
              github.getUserStateWithToken(token).map { user =>
                githubCredentialsCache(token) = (credentials, user)
                Some((credentials, user))
              }
            }
          }

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
    val size = segments.size
    val takeFrom = if (segments.head.isEmpty) 1 else 0

    val artifactId = segments(size - 3)
    val version = segments(size - 2)
    val groupId = segments.slice(takeFrom, size - 3).mkString(".")

    MavenReference(groupId, artifactId, version)
  }

  import akka.pattern.ask

  import scala.concurrent.duration._
  implicit val timeout: Timeout = Timeout(40.seconds)
  private val actor =
    system.actorOf(
      Props(classOf[impl.PublishActor], paths, dataRepository, system)
    )

  private val githubCredentialsCache =
    MMap.empty[String, (data.github.Credentials, UserState)]

  val DateTimeUn: Unmarshaller[String, DateTime] =
    Unmarshaller.strict[String, DateTime] { dateRaw =>
      new DateTime(dateRaw.toLong * 1000L)
    }

  val routes: Route =
    concat(
      get(
        path("publish")(
          parameter("path")(path =>
            complete(
              /* check if the release already exists - sbt will handle HTTP-Status codes
               * NotFound -> allowed to write
               * OK -> only allowed if isSnapshot := true
               */
              dataRepository.getMavenArtifact(mavenPathExtractor(path)) map {
                case Some(release) => (OK, "release already exists")
                case None => (NotFound, "ok to publish")
              }
            )
          )
        )
      ),
      put(
        path("publish")(
          parameters(
            (
              "path",
              "created".as(DateTimeUn) ? DateTime.now,
              "readme".as[Boolean] ? true,
              "contributors".as[Boolean] ? true,
              "info".as[Boolean] ? true
            )
          )((path, created, readme, contributors, info) =>
            entity(as[String])(data =>
              extractCredentials(credentials =>
                authenticateBasicAsync(
                  realm = "Scaladex Realm",
                  githubAuthenticator(credentials)
                ) { case (credentials, userState) =>
                  val publishData = impl.PublishData(
                    path,
                    created,
                    data,
                    credentials,
                    userState,
                    info,
                    contributors,
                    readme
                  )

                  log.info(
                    s"Received publish command: ${publishData.created} - ${publishData.path}"
                  )
                  log.debug(publishData.data)

                  complete(
                    (actor ? publishData)
                      .mapTo[(StatusCode, String)]
                      .map(s => s)
                  )
                }
              )
            )
          )
        )
      )
    )
}

object PublishApi {
  def apply(
      paths: DataPaths,
      dataRepository: ESRepo
  )(implicit sys: ActorSystem): PublishApi = {
    new PublishApi(paths, dataRepository, Github())
  }
}
