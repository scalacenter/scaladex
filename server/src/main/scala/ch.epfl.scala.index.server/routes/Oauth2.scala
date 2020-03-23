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
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.server.config.{OAuth2Config, ServerConfig}
import headers.Referer
import server.Directives._

import scala.concurrent.ExecutionContext

class Oauth2(config: OAuth2Config, github: Github, session: GithubUserSession)(
    implicit ec: ExecutionContext
) {
  import session.implicits._

  val routes: Route =
    get(
      concat(
        path("login")(
          optionalHeaderValueByType[Referer](())(
            referer =>
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
              parameters(('code, 'state.?)) { (code, state) =>
                val userStateQuery = github.getUserStateWithOauth2(
                  config.clientId,
                  config.clientSecret,
                  code,
                  config.redirectUri
                )

                onSuccess(userStateQuery) {
                  userState =>
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
      session: GithubUserSession
  )(implicit sys: ActorSystem, mat: ActorMaterializer): Oauth2 = {
    import sys.dispatcher
    new Oauth2(config.oAuth2, Github(), session)
  }
}
