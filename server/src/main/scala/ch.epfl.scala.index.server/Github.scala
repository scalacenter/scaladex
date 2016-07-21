package ch.epfl.scala.index
package server

import model.misc._
import data.github._

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import unmarshalling.Unmarshal

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future

import com.typesafe.config.ConfigFactory

case class AccessTokenResponse(access_token: String)
case class RepoResponse(full_name: String)
case class UserResponse(login: String, name: Option[String], avatar_url: String)

case class UserState(repos: Set[GithubRepo], user: UserInfo) {
  def isAdmin = repos.exists {
    case GithubRepo(organization, repository) =>
      organization == "scalacenter" &&
        repository == "scaladex"
  }
}

class Github(implicit system: ActorSystem, materializer: ActorMaterializer) extends Json4sSupport {
  import system.dispatcher

  val config       = ConfigFactory.load().getConfig("org.scala_lang.index.oauth2")
  val clientId     = config.getString("client-id")
  val clientSecret = config.getString("client-secret")
  val redirectUri  = config.getString("redirect-uri")

  def info(code: String) = {
    def access = {
      Http()
        .singleRequest(
            HttpRequest(
                method = POST,
                uri = Uri("https://github.com/login/oauth/access_token").withQuery(
                    Query(
                        "client_id"     -> clientId,
                        "client_secret" -> clientSecret,
                        "code"          -> code,
                        "redirect_uri"  -> redirectUri
                    )),
                headers = List(Accept(MediaTypes.`application/json`))
            ))
        .flatMap(response => Unmarshal(response).to[AccessTokenResponse].map(_.access_token))
    }
    def fetchGithub(token: String, path: Path, query: Query = Query.Empty) = {
      Http().singleRequest(
          HttpRequest(
              uri = Uri(s"https://api.github.com").withPath(path).withQuery(query),
              headers = List(Authorization(GenericHttpCredentials("token", token)))
          ))
    }

    def fetchRepos(token: String): Future[List[RepoResponse]] = {
      def request(page: Option[Int] = None) = {
        val query = page
          .map(p => Query("visibility"  -> "public", "page" -> p.toString()))
          .getOrElse(Query("visibility" -> "public"))
        fetchGithub(token, Path.Empty / "user" / "repos", query)
      }
      request(None).flatMap { r1 =>
        val lastPage = r1.headers.find(_.name == "Link").map(h => extractLastPage(h.value)).getOrElse(1)
        Unmarshal(r1).to[List[RepoResponse]].map(repos => (repos, lastPage))
      }.flatMap {
        case (repos, lastPage) =>
          val nextPagesRequests = if (lastPage > 1) {
            Future
              .sequence(
                  (2 to lastPage).map(page => request(Some(page)).flatMap(r2 => Unmarshal(r2).to[List[RepoResponse]])))
              .map(_.flatten)
          } else Future.successful(Nil)

          nextPagesRequests.map(repos2 => repos ++ repos2)
      }
    }

    def fetchUser(token: String) =
      fetchGithub(token, Path.Empty / "user").flatMap(response => Unmarshal(response).to[UserResponse])

    for {
      token         <- access
      (repos, user) <- fetchRepos(token).zip(fetchUser(token))
    } yield {
      val githubRepos = repos.map { r =>
        val List(user, repo) = r.full_name.split("/").toList
        GithubRepo(user, repo)
      }.toSet
      val UserResponse(login, name, avatarUrl) = user

      UserState(githubRepos, UserInfo(login, name, avatarUrl))
    }
  }
}
