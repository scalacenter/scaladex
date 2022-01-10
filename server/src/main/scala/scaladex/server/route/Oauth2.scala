package scaladex.server.route

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes.TemporaryRedirect
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Referer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import scaladex.server.GithubAuth
import scaladex.server.GithubUserSession
import scaladex.server.config.OAuth2Config

class Oauth2(config: OAuth2Config, githubAuth: GithubAuth, session: GithubUserSession)(
    implicit ec: ExecutionContext
) {
  import session.implicits._

  val routes: Route =
    get(
      concat(
        path("login")(
          optionalHeaderValueByType(Referer)(referer =>
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
          headerValueByType(Referer) { referer =>
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
              parameters("code", "state".?) { (code, state) =>
                val userStateQuery = githubAuth.getUserStateWithOauth2(
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
