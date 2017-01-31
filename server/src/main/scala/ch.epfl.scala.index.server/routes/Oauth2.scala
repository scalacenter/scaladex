package ch.epfl.scala.index
package server
package routes

import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._

import akka.http.scaladsl._
import model._
import Uri.Query
import StatusCodes.TemporaryRedirect
import headers.Referer
import server.Directives._

class OAuth2(github: Github, session: GithubUserSession) {
  import session._

  val routes =
    get {
      path("login") {
        headerValueByType[Referer]() { referer =>
          redirect(
            Uri("https://github.com/login/oauth/authorize").withQuery(
              Query(
                "client_id" -> github.clientId,
                "scope" -> "read:org",
                "state" -> referer.value
              )),
            TemporaryRedirect
          )
        }
      } ~
        path("logout") {
          headerValueByType[Referer]() { referer =>
            requiredSession(refreshable, usingCookies) { _ =>
              invalidateSession(refreshable, usingCookies) { ctx =>
                ctx.complete(
                  HttpResponse(
                    status = TemporaryRedirect,
                    headers = headers.Location(Uri(referer.value)) :: Nil,
                    entity = HttpEntity.Empty
                  )
                )
              }
            }
          }
        } ~
        pathPrefix("callback") {
          path("done") {
            complete("OK")
          } ~
            pathEnd {
              parameters('code, 'state.?) { (code, state) =>
                onSuccess(github.getUserStateWithOauth2(code)) { userState =>
                  setSession(refreshable, usingCookies, session.addUser(userState)) {
                    setNewCsrfToken(checkHeader) { ctx =>
                      ctx.complete(
                        HttpResponse(
                          status = TemporaryRedirect,
                          headers = headers.Location(Uri(state.getOrElse("/"))) :: Nil,
                          entity = HttpEntity.Empty
                        )
                      )
                    }
                  }
                }
              }
            }
        }
    }
}
