package scaladex.server

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import scaladex.core.model.GithubResponse
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.service.GithubAuth
import scaladex.core.service.GithubClient
import scaladex.core.util.Secret
import scaladex.infra.GithubClientImpl
import scaladex.server.config.OAuth2Config

private class GithubAuthImpl(clientId: String, clientSecret: String, redirectUri: String)(implicit sys: ActorSystem)
    extends GithubAuth
    with FailFastCirceSupport
    with LazyLogging {
  import sys.dispatcher

  private val githubClients: TrieMap[Secret, GithubClient] = TrieMap()

  def getToken(code: String): Future[Secret] =
    Http()
      .singleRequest(
        HttpRequest(
          method = POST,
          uri = Uri("https://github.com/login/oauth/access_token").withQuery(
            Query(
              "client_id" -> clientId,
              "client_secret" -> clientSecret,
              "code" -> code,
              "redirect_uri" -> redirectUri
            )
          ),
          headers = List(Accept(MediaTypes.`application/json`))
        )
      )
      .flatMap { response =>
        Unmarshal(response)
          .to[Json]
          .map(json => json.hcursor.downField("access_token").as[String].fold(throw _, identity))
          .map(Secret.apply)
      }

  def getUser(token: Secret): Future[UserInfo] = {
    val githubClient = githubClients.getOrElseUpdate(token, new GithubClientImpl(token))
    githubClient.getUserInfo().map {
      case GithubResponse.Ok(res)               => res
      case GithubResponse.MovedPermanently(res) => res
      case GithubResponse.Failed(errorCode, errorMessage) =>
        val message = s"Failed to get user state: $errorCode, $errorMessage"
        throw new Exception(message)
    }
  }

  def getUserState(token: Secret): Future[Option[UserState]] = {
    val githubClient = githubClients.getOrElseUpdate(token, new GithubClientImpl(token))
    githubClient.getUserState().map {
      case GithubResponse.Ok(userState)               => Some(userState)
      case GithubResponse.MovedPermanently(userState) => Some(userState)
      case GithubResponse.Failed(errorCode, errorMessage) =>
        logger.warn(s"Failed to get user state: $errorCode, $errorMessage")
        None
    }
  }
}

object GithubAuthImpl {
  def apply(config: OAuth2Config)(implicit sys: ActorSystem): GithubAuth =
    new GithubAuthImpl(config.clientId, config.clientSecret, config.redirectUri)
}
