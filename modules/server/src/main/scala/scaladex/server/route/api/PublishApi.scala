package scaladex.server.route.api

import java.time.Instant

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.UserState
import scaladex.core.service.GithubAuth
import scaladex.core.util.Secret
import scaladex.server.route._
import scaladex.server.service.PublishProcess
import scaladex.server.service.PublishResult

class PublishApi(githubAuth: GithubAuth, publishProcess: PublishProcess)(implicit ec: ExecutionContext)
    extends LazyLogging {

  private val credentialsCache: TrieMap[Secret, UserState] =
    TrieMap.empty[Secret, UserState]

  private val authenticateUser: Directive1[UserState] =
    extractCredentials.flatMap {
      case Some(BasicHttpCredentials("token" | "central-ossrh", password)) =>
        val token = Secret(password)
        credentialsCache.get(token) match {
          case Some(userState) => provide(userState)
          case None =>
            onSuccess(githubAuth.getUserState(token)).flatMap {
              case Some(userState) =>
                credentialsCache.update(token, userState)
                provide(userState)
              case None => reject(AuthorizationFailedRejection)
            }
        }
      case Some(BasicHttpCredentials(user, _)) =>
        logger.warn(s"Rejected basic authentication of $user")
        reject(AuthorizationFailedRejection)
      case Some(other: HttpCredentials) =>
        logger.warn(s"Rejected authentication with scheme ${other.scheme}")
        reject(AuthorizationFailedRejection)
      case None => reject(AuthorizationFailedRejection)
    }

  val routes: Route =
    concat(
      get(
        path("publish")(
          parameter("path")(_ =>
            complete {
              /* check if the artifact already exists - sbt will handle HTTP-Status codes
               * NotFound -> allowed to write
               * OK -> only allowed if isSnapshot := true
               */
              val alreadyPublished = false // TODO check from database
              if (alreadyPublished) (StatusCodes.OK, "artifact already exists")
              else (StatusCodes.NotFound, "ok to publish")
            }
          )
        )
      ),
      put(
        path("publish")(
          parameters("path", "created".as(instantUnmarshaller) ? Instant.now()) { (path, created) =>
            entity(as[String]) { data =>
              authenticateUser { userState =>
                logger.info(s"Received publish command: $created - $path")
                val result = publishProcess.publishPom(path, data, created, Some(userState))

                complete(
                  result.map {
                    case PublishResult.InvalidPom   => (StatusCodes.BadRequest, "pom is invalid")
                    case PublishResult.NoGithubRepo => (StatusCodes.NoContent, "github repository not found")
                    case PublishResult.Success      => (StatusCodes.Created, "pom published successfully")
                    case PublishResult.Forbidden(login, repo) =>
                      (StatusCodes.Forbidden, s"$login cannot publish to $repo")
                  }
                )
              }
            }
          }
        )
      )
    )
}
