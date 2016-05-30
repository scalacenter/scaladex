package ch.epfl.scala.index
package server

import api._
import model.GithubRepo

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._

import akka.http.scaladsl._
import akka.http.scaladsl.model._, HttpMethods.POST, headers._, Uri._

import akka.http.scaladsl.unmarshalling.Unmarshal

import com.softwaremill.session._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

case class AccessTokenResponse(access_token: String)
case class RepoResponse(full_name: String)
case class UserResponse(login: String, name: String, avatar_url: String)

trait GithubProtocol extends DefaultJsonProtocol {
  implicit val formatAccessTokenResponse = jsonFormat1(AccessTokenResponse)
  implicit val formatRepoResponse = jsonFormat1(RepoResponse)
  implicit val formatUserResponse = jsonFormat3(UserResponse)
}

case class UserState(repos: Set[GithubRepo], user: UserInfo)
object UserState extends DefaultJsonProtocol {
  implicit val formatGithubRepo = jsonFormat2(GithubRepo)
  implicit val formatUserInfo = jsonFormat3(UserInfo)
  implicit val formatUserState = jsonFormat2(UserState.apply)
  implicit def serializer: SessionSerializer[UserState, String] = new SingleValueSessionSerializer(
    _.toJson.compactPrint,
    (in: String) => Try { in.parseJson.convertTo[UserState] }
  )
}

class Github(implicit system: ActorSystem, materializer: ActorMaterializer) extends GithubProtocol {
  import system.dispatcher

  val clientId = "803749a6b539a950f01a"
  val clientSecret = "80563c1ae6cd26f2327a346b4e8844680fee652e"
  def info(code: String) = {
    def access = {
      Http().singleRequest(HttpRequest(
        method = POST,
        uri = Uri("https://github.com/login/oauth/access_token").withQuery(Query(
          "client_id" -> clientId,
          "client_secret" -> clientSecret,
          "code" -> code,
          "redirect_uri" -> "http://localhost:8080/callback/done"
        )),
        headers = List(Accept(MediaTypes.`application/json`))
      )).flatMap(response =>
        Unmarshal(response).to[AccessTokenResponse].map(_.access_token)
      )
    }
    def fetchGithub(token: String, path: Path, query: Query = Query.Empty) = {
      Http().singleRequest(HttpRequest(
        uri = Uri(s"https://api.github.com").withPath(path),
        headers = List(Authorization(GenericHttpCredentials("token", token)))
      ))
    }

    def fetchRepos(token: String) =
      fetchGithub(token, Path.Empty / "user" / "repos", Query("visibility" -> "public")).flatMap(response => 
        Unmarshal(response).to[List[RepoResponse]]
      )

    def fetchUser(token: String) =
      fetchGithub(token, Path.Empty / "user").flatMap(response => 
        Unmarshal(response).to[UserResponse]
      )        

    for {
      token         <- access
      (repos, user) <- fetchRepos(token).zip(fetchUser(token))
    } yield {
      val githubRepos = repos.map{ r =>
        val List(user, repo) = r.full_name.split("/").toList
        GithubRepo(user, repo)
      }.toSet
      val UserResponse(login, name, avatarUrl) = user

      UserState(githubRepos, UserInfo(login, name, avatarUrl))
    }
  }

  def fetchReadme(githubRepo: GithubRepo): Future[Option[GithubReadme]] = {
    // change
    val token = "3309a24669ab11973c08517e5ddcfbc9a329cb44"
    val GithubRepo(user, repo) = githubRepo

    def request =
      Http().singleRequest(HttpRequest(
        uri = Uri(s"https://api.github.com").withPath(Path.Empty / "repos" / user / repo / "readme"),
        headers = List(
          Authorization(GenericHttpCredentials("token", token)),
          Accept(List(MediaRange.custom("application/vnd.github.VERSION.html")))
        )
      ))

    request
      .flatMap(response => Unmarshal(response).to[String])
      .map(html => Some(GithubReadme(absoluteUrl(html, githubRepo))))
      .recover{case NonFatal(e) => {
        println(e)
        None
      }}
  }

  private def absoluteUrl(readmeHtml: String, githubRepo: GithubRepo): String = {
    val GithubRepo(user, repo) = githubRepo
    import org.jsoup.Jsoup
    
    // github returns absolute url we need a "safe way" to replace the
    val someUrl = "http://NTU1ZTAwY2U2YTljZGZjOTYyYjg5NGZh.com"

    val doc = Jsoup.parse(readmeHtml, someUrl)

    val root = s"https://github.com/$user/$repo"
    def base(v: String) = s"$root/$v/master"
    val raw = base("raw")
    val blob = base("blob")
    
    doc.select("a, img").toArray
      .map(_.asInstanceOf[org.jsoup.nodes.Element])
      .foreach{e => 
        val (at, replace) =
          if(e.tagName == "a") {
            val attr = "href"
            val href = 
              if(e.attr(attr).startsWith("#")) root
              else blob
            (attr, href)
          }
          else ("src", raw)
        
        e.attr(at, e.absUrl(at).replaceAllLiterally(someUrl, replace))
      }

    doc.body.childNodes.toArray.mkString("")
  }
}