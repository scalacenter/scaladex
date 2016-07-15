package ch.epfl.scala.index
package server

import model.misc.{GithubRepo, UserInfo}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import akka.http.scaladsl.unmarshalling.Unmarshal

import com.softwaremill.session._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.util.Try

case class AccessTokenResponse(access_token: String)
case class RepoResponse(full_name: String)
case class UserResponse(login: String, name: String, avatar_url: String)

trait GithubProtocol extends DefaultJsonProtocol {
  implicit val formatAccessTokenResponse = jsonFormat1(AccessTokenResponse)
  implicit val formatRepoResponse        = jsonFormat1(RepoResponse)
  implicit val formatUserResponse        = jsonFormat3(UserResponse)
}

case class UserState(repos: Set[GithubRepo], user: UserInfo)
object UserState extends DefaultJsonProtocol {
  implicit val formatGithubRepo = jsonFormat2(GithubRepo)
  implicit val formatUserInfo   = jsonFormat3(UserInfo)
  implicit val formatUserState  = jsonFormat2(UserState.apply)
  implicit def serializer: SessionSerializer[UserState, String] =
    new SingleValueSessionSerializer(
        _.toJson.compactPrint,
        (in: String) => Try { in.parseJson.convertTo[UserState] }
    )
}

class Github(implicit system: ActorSystem, materializer: ActorMaterializer)
    extends GithubProtocol {
  import system.dispatcher

  // scaladex user
  val clientId     = "62ce08c1867245d61742"
  val clientSecret = "2e968758c2e512f3c1e7b51a3bb1677130a3c4f6"

  def info(code: String) = {
    def access = {
      Http()
        .singleRequest(
            HttpRequest(
                method = POST,
                uri =
                  Uri("https://github.com/login/oauth/access_token").withQuery(
                      Query(
                          "client_id"     -> clientId,
                          "client_secret" -> clientSecret,
                          "code"          -> code,
                          "redirect_uri"  -> "http://localhost:8080/callback/done"
                      )),
                headers = List(Accept(MediaTypes.`application/json`))
            ))
        .flatMap(response =>
              Unmarshal(response).to[AccessTokenResponse].map(_.access_token))
    }
    def fetchGithub(token: String, path: Path, query: Query = Query.Empty) = {
      Http().singleRequest(
          HttpRequest(
              uri = Uri(s"https://api.github.com").withPath(path),
              headers =
                List(Authorization(GenericHttpCredentials("token", token)))
          ))
    }

    def fetchRepos(token: String) =
      fetchGithub(token,
                  Path.Empty / "user" / "repos",
                  Query("visibility" -> "public")).flatMap(response =>
            Unmarshal(response).to[List[RepoResponse]])

    def fetchUser(token: String) =
      fetchGithub(token, Path.Empty / "user").flatMap(response =>
            Unmarshal(response).to[UserResponse])

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
