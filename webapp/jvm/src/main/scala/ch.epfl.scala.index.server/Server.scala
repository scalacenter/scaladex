package ch.epfl.scala.index
package server

import api._
import model.Artifact

import upickle.default.{read => uread}

import akka.http.scaladsl._
import akka.http.scaladsl.model._, Uri._, StatusCodes.TemporaryRedirect

import com.softwaremill.session._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github

    val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
    implicit val sessionManager = new SessionManager[UserState](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UserState] {
      def log(msg: String) = println(msg)
    }

    val sharedApi = new ApiImplementation(github, None)
    val rest = new RestApi(sharedApi)

    def reuseSharedApi(userState: Option[UserState]) =
      if(userState.isDefined) new ApiImplementation(github, userState)
      else sharedApi

    val route = {
      import akka.http.scaladsl._
      import server.Directives._
      import TwirlSupport._

      post {
        path("autowire" / Segments){ s ⇒
          entity(as[String]) { e ⇒
            optionalSession(refreshable, usingCookies) { userState =>
              complete {
                AutowireServer.route[Api](reuseSharedApi(userState))(
                  autowire.Core.Request(s, uread[Map[String, String]](e))
                )
              }
            }
          }
        }
      } ~
      rest.route ~
      get {
        path("login") {
          redirect(Uri("https://github.com/login/oauth/authorize").withQuery(Query(
            "client_id" -> github.clientId
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
              val userState = Await.result(github.info(code), 10.seconds)
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
        path("assets" / Remaining) { path ⇒
          getFromResource(path)
        } ~
        pathSingleSlash {
          complete(Template.home)
        } ~
        path("project" / Remaining) { _ ⇒
          complete(Template.home)
        } ~
        path("poc" / Segment / Segment) { (owner:String, artifactName: String) =>
          complete(
            sharedApi.projectPage(Artifact.Reference(owner, artifactName)).map(project =>
              project.map(p => html.project.render(p))
            )
          )
        } ~
        path("search" / Segment) { query:String =>
          complete(html.searchresult.render(query))
        }
      }
    }

    val setup = for {
      _ <- ApiImplementation.setup
      _ <- Http().bindAndHandle(route, "localhost", 8080)
    } yield ()
    Await.result(setup, 20.seconds)

    ()
  }
}

