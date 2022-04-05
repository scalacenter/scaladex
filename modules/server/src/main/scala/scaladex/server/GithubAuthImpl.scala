package scaladex.server

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.GithubResponse
import scaladex.core.model.UserState
import scaladex.core.service.GithubAuth
import scaladex.core.util.Secret
import scaladex.infra.GithubClient

object Response {
  case class AccessToken(access_token: String) {
    val token: Secret = Secret(access_token)
  }
}
//todo: remove Json4sSupport
class GithubAuthImpl(clientId: String, clientSecret: String, redirectUri: String)(implicit sys: ActorSystem)
    extends GithubAuth
    with Json4sSupport
    with LazyLogging {
  import sys.dispatcher

  def getUserStateWithToken(token: String): Future[UserState] = getUserState(Secret(token))

  def getUserStateWithOauth2(code: String): Future[UserState] =
    for {
      token <- getTokenWithOauth2(code)
      userState <- getUserState(token)
    } yield userState

  private def getUserState(token: Secret): Future[UserState] = {
    val githubClient = new GithubClient(token)
    githubClient.getUserState().flatMap {
      case GithubResponse.Ok(res)               => Future.successful(res)
      case GithubResponse.MovedPermanently(res) => Future.successful(res)
      case GithubResponse.Failed(errorCode, errorMessage) =>
        val message = s"Call to GithubClient#getUserState failed with code: $errorCode, message: $errorMessage"
        Future.failed(new Exception(message))
    }
  }

  private def getTokenWithOauth2(code: String): Future[Secret] =
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
      .flatMap(response => Unmarshal(response).to[Response.AccessToken].map(_.token))
}
