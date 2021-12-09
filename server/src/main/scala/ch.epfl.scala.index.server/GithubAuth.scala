package ch.epfl.scala.index
package server

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.services.GithubService
import ch.epfl.scala.utils.Secret

object Response {
  case class AccessToken(access_token: String)
}

class GithubAuth(githubClient: GithubService)(implicit sys: ActorSystem) extends Json4sSupport {
  import sys.dispatcher

  def getUserStateWithToken(token: String): Future[UserState] = info(token)
  def getUserStateWithOauth2(
      clientId: String,
      clientSecret: String,
      code: String,
      redirectUri: String
  ): Future[UserState] = {
    def access =
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
        .flatMap(response => Unmarshal(response).to[Response.AccessToken].map(_.access_token))

    access.flatMap(info)
  }

  private def info(token: String): Future[UserState] = {
    val permissions = Seq("WRITE", "MAINTAIN", "ADMIN")
    def filterAndConvert(repos: Map[GithubRepo, String]): Set[GithubRepo] =
      repos.collect { case (repo, permission) if permissions.contains(permission) => repo }.toSet
    val secret = Secret(token)
    for {
      user <- githubClient.fetchUser(secret)
      organizations <- githubClient.fetchUserOrganizations(secret)
      repos <- githubClient.fetchUserRepo(secret)
      githubRepo = filterAndConvert(repos)
    } yield UserState(githubRepo, organizations, user)
  }
}

object GithubAuth {
  def apply(client: GithubService)(implicit sys: ActorSystem) = new GithubAuth(client)
}
