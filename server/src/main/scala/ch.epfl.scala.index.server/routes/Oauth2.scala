package ch.epfl.scala.index
package server
package routes

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.TemporaryRedirect
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Referer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.scala.index.server.config.OAuth2Config
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.services.GithubService
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

class Oauth2(config: OAuth2Config, github: GithubAuth, session: GithubUserSession)(
    implicit ec: ExecutionContext
) {
  import session.implicits._

  val routes: Route =
    get(
      concat(
        path("login")(
          optionalHeaderValueByType[Referer](())(referer =>
            redirect(
              Uri("https://github.com/login/oauth/authorize").withQuery(
                Query(
                  "client_id" -> config.clientId,
                  "scope" -> "read:org",
                  "state" -> referer.map(_.value).getOrElse("/")
                )
              ),
              TemporaryRedirect
            )
          )
        ),
        path("logout")(
          headerValueByType[Referer](()) { referer =>
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
        ),
        pathPrefix("callback")(
          concat(
            path("done")(
              complete("OK")
            ),
            pathEnd(
              parameters(("code", "state".?)) { (code, state) =>
                val userStateQuery = github.getUserStateWithOauth2(
                  config.clientId,
                  config.clientSecret,
                  code,
                  config.redirectUri
                )

                onSuccess(userStateQuery) { userState =>
                  setSession(
                    refreshable,
                    usingCookies,
                    session.addUser(userState)
                  )(
                    setNewCsrfToken(checkHeader) { ctx =>
                      ctx.complete(
                        HttpResponse(
                          status = TemporaryRedirect,
                          headers = headers
                            .Location(Uri(state.getOrElse("/"))) :: Nil,
                          entity = HttpEntity.Empty
                        )
                      )
                    }
                  )
                }
              }
            )
          )
        )
      )
    )
}

object Oauth2 {
  def apply(
      config: ServerConfig,
      session: GithubUserSession,
      githubService: GithubService
  )(implicit sys: ActorSystem): Oauth2 = {
    import sys.dispatcher
    new Oauth2(config.oAuth2, GithubAuth(githubService), session)
  }
}
