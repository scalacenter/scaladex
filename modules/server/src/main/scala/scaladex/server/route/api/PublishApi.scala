package scaladex.server.route.api

import java.time.Instant
import java.util.Base64

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives._
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.UserState
import scaladex.core.service.GithubAuth
import scaladex.server.route._
import scaladex.server.service.PublishProcess
import scaladex.server.service.PublishResult

class PublishApi(
    github: GithubAuth,
    publishProcess: PublishProcess
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  /*
   * verifying a login to github
   * @param credentials the credentials
   * @return
   */
  private def githubAuthenticator(
      credentialsHeader: Option[HttpCredentials]
  ): Credentials => Future[Option[(String, UserState)]] = {

    case Credentials.Provided(username) =>
      credentialsHeader match {
        case Some(cred) =>
          val upw = new String(
            Base64.getDecoder.decode(cred.token())
          )
          val userPass = upw.split(":")

          val token = userPass(1)
          // todo - catch errors

          githubCredentialsCache.get(token) match {
            case res @ Some(_) =>
              Future.successful(res)
            case _ =>
              github.getUserStateWithToken(token).map { user =>
                githubCredentialsCache(token) = (token, user)
                Some((token, user))
              }
          }

        case _ => Future.successful(None)
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
  private def mavenPathExtractor(path: String): Artifact.MavenReference = {

    val segments = path.split("/").toList
    val size = segments.size
    val takeFrom = if (segments.head.isEmpty) 1 else 0

    val artifactId = segments(size - 3)
    val version = segments(size - 2)
    val groupId = segments.slice(takeFrom, size - 3).mkString(".")

    Artifact.MavenReference(groupId, artifactId, version)
  }

  private val githubCredentialsCache =
    MMap.empty[String, (String, UserState)]

  val routes: Route =
    concat(
      get(
        path("publish")(
          parameter("path")(path =>
            complete {
              /* check if the artifact already exists - sbt will handle HTTP-Status codes
               * NotFound -> allowed to write
               * OK -> only allowed if isSnapshot := true
               */
              val alreadyPublished = false // TODO check from database
              if (alreadyPublished) (OK, "artifact already exists")
              else (NotFound, "ok to publish")
            }
          )
        )
      ),
      put(
        path("publish")(
          parameters(
            "path",
            "created".as(instantUnmarshaller) ? Instant.now(),
            "readme".as[Boolean] ? true,
            "contributors".as[Boolean] ? true,
            "info".as[Boolean] ? true
          )((path, created, _, _, _) =>
            entity(as[String])(data =>
              extractCredentials(credentials =>
                authenticateBasicAsync(
                  realm = "Scaladex Realm",
                  githubAuthenticator(credentials)
                ) {
                  case (_, userState) =>
                    logger.info(s"Received publish command: $created - $path")
                    val result = publishProcess.publishPom(path, data, created, Some(userState))

                    complete(
                      result.map {
                        case PublishResult.InvalidPom   => (StatusCodes.BadRequest, "pom is invalid")
                        case PublishResult.NoGithubRepo => (StatusCodes.NoContent, "github repository not found")
                        case PublishResult.Success      => (StatusCodes.Created, "pom published successfully")
                        case PublishResult.Forbidden(login, repo) =>
                          (StatusCodes.Forbidden, s"$login cannot publish ro $repo")
                      }
                    )
                }
              )
            )
          )
        )
      )
    )
}
