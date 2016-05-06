package ch.epfl.scala.index

import cleanup._
import bintray._

import elastic._
import com.sksamuel.elastic4s._
import ElasticDsl._

import upickle.default.{Reader, Writer, write => uwrite, read => uread}

import akka.http.scaladsl._
import akka.http.scaladsl.model._, HttpMethods.POST, headers._, Uri._, StatusCodes.TemporaryRedirect

import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import akka.http.scaladsl.model.MediaTypes.`application/json`

import com.softwaremill.session._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

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

object Server extends GithubProtocol {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
    implicit val sessionManager = new SessionManager[UserState](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UserState] {
      def log(msg: String) = println(msg)
    }

    val index = {
      import scalatags.Text.all._
      import scalatags.Text.tags2.title

      "<!DOCTYPE html>" +
      html(
        head(
          title("Scaladex"),
          base(href:="/"),
          meta(charset:="utf-8")
        ),
        body(
          script(src:="/assets/webapp-jsdeps.js"),
          script(src:="/assets/webapp-fastopt.js"),
          script("ch.epfl.scala.index.Client().main()")
        )
      )
    }

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

    class ScaladexApi(userState: Option[UserState]) extends Api {
      def find(q: String): Future[(Long, List[Project])] = {
        esClient.execute {
          search.in(indexName / collectionName) query q
        }.map(r => (r.totalHits, r.as[Project].toList))
      }
      def userInfo(): Option[UserInfo] = {
        userState.map(_.user)
      }
    }
    
    val route = {
      import akka.http.scaladsl._
      import server.Directives._

      post {
        path("api" / Segments){ s ⇒
          entity(as[String]) { e ⇒
            optionalSession(refreshable, usingCookies) { userState =>
              complete {
                AutowireServer.route[Api](new ScaladexApi(userState))(
                  autowire.Core.Request(s, uread[Map[String, String]](e))
                )
              }
            }
          }
        }
      } ~
      get {
        path("login") {
          redirect(Uri("https://github.com/login/oauth/authorize").withQuery(Query(
            "client_id" -> clientId
          )),TemporaryRedirect)
        } ~
        path("logout") {
          requiredSession(refreshable, usingCookies) { _ =>
            invalidateSession(refreshable, usingCookies) { ctx =>
              ctx.complete("{}")
            }
          }
        } ~
        pathPrefix("callback") {
          path("done") {
            complete("OK")
          } ~
          pathEnd {
            parameter('code) { code =>
              val userState = Await.result(info(code), 10.seconds)
              setSession(refreshable, usingCookies, userState) {
                setNewCsrfToken(checkHeader) { ctx => 
                  ctx.complete("ok") 
                }
              }
              
              // A popup was open for Oauth2
              // We notify the opening window
              // We close the popup
              // complete(script("""|window.opener.oauth2()
              //                    |window.close()"""))
            }
          }
        } ~
        path("assets" / Rest) { path ⇒
          getFromResource(path)
        } ~
        pathSingleSlash {
         complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, index)))
        }
      }
    }

    val setup = for {
      _ <- Http().bindAndHandle(route, "localhost", 8080)
      _ <- esClient.execute { indexExists(indexName) }
    } yield ()
    Await.result(setup, 20.seconds)

    ()
  } 
}

object AutowireServer extends autowire.Server[String, Reader, Writer]{
  def read[Result: Reader](p: String)  = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}