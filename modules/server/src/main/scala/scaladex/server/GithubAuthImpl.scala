package scaladex.server

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import scaladex.core.model.UserState
import scaladex.core.service.GithubAuth
import scaladex.core.util.ScalaExtensions._
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
    with Json4sSupport {
  import sys.dispatcher

  def getUserStateWithToken(token: String): Future[UserState] = getUserState(Secret(token))

  def getUserStateWithOauth2(code: String): Future[UserState] =
    for {
      token <- getTokenWithOauth2(code)
      userState <- getUserState(token)
    } yield userState

  private def getUserState(token: Secret): Future[UserState] = {
    val githubClient = new GithubClient(token)
    val permissions = Seq("WRITE", "MAINTAIN", "ADMIN")
    for {
      user <- githubClient.getUserInfo()
      orgas <- githubClient.getUserOrganizations(user.login).recover { case _ => Seq.empty }
      orgasRepos <- orgas
        .map { org =>
          githubClient.getOrganizationRepositories(user.login, org, permissions).recover { case _ => Seq.empty }
        }
        .sequence
        .map(_.flatten)
      userRepos <- githubClient.getUserRepositories(user.login, permissions).recover { case _ => Seq.empty }
    } yield UserState(orgasRepos.toSet ++ userRepos, orgas.toSet, user)
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
