package scaladex.server.route

import java.util.UUID

import scala.util.Success
import scala.util.Try

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.StatusCodes.TemporaryRedirect
import org.apache.pekko.http.scaladsl.model.Uri.Query
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.Referer
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import com.softwaremill.pekkohttpsession.CsrfDirectives._
import com.softwaremill.pekkohttpsession.CsrfOptions._
import com.softwaremill.pekkohttpsession.SessionConfig
import com.softwaremill.pekkohttpsession.SessionDirectives._
import com.softwaremill.pekkohttpsession.SessionManager
import com.softwaremill.pekkohttpsession.SessionOptions._
import com.softwaremill.pekkohttpsession.SessionSerializer
import com.softwaremill.pekkohttpsession.SingleValueSessionSerializer
import com.softwaremill.pekkohttpsession.javadsl.InMemoryRefreshTokenStorage
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.UserState
import scaladex.core.service.GithubAuth
import scaladex.core.service.WebDatabase

class AuthenticationApi(clientId: String, sessionConfig: SessionConfig, githubAuth: GithubAuth, database: WebDatabase)(
    implicit system: ActorSystem
) extends LazyLogging {

  import system.dispatcher
  implicit val serializer: SessionSerializer[UUID, String] =
    new SingleValueSessionSerializer(_.toString(), (id: String) => Try(UUID.fromString(id)))
  implicit val sessionManager: SessionManager[UUID] = new SessionManager[UUID](sessionConfig)
  implicit val refreshTokenStorage: InMemoryRefreshTokenStorage[UUID] =
    (msg: String) =>
      if (msg.startsWith("Looking up token for selector")) () // borring
      else logger.info(msg)

  def optionalUser: Directive1[Option[UserState]] =
    optionalSession(refreshable, usingCookies).flatMap {
      case None         => provide(None)
      case Some(userId) => onSuccess(database.getUser(userId))
    }

  val routes: Route =
    get(
      concat(
        path("login")(
          optionalHeaderValueByType(Referer)(referer =>
            redirect(
              Uri("https://github.com/login/oauth/authorize").withQuery(
                Query(
                  "client_id" -> clientId,
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
            path("done")(complete("OK")),
            pathEnd(
              parameters("code", "state".?) { (code, state) =>
                val future = for {
                  token <- githubAuth.getToken(code)
                  user <- githubAuth.getUser(token)
                  userId = UUID.randomUUID()
                  _ <- database.insertUser(userId, user)
                } yield {
                  // Update user state lazily
                  githubAuth.getUserState(token).andThen {
                    case Success(Some(userState)) => database.updateUser(userId, userState)
                  }

                  setSession(refreshable, usingCookies, userId)(
                    setNewCsrfToken(checkHeader) { ctx =>
                      ctx.complete(
                        HttpResponse(
                          status = TemporaryRedirect,
                          headers = headers.Location(Uri(state.getOrElse("/"))) :: Nil,
                          entity = HttpEntity.Empty
                        )
                      )
                    }
                  )
                }
                onSuccess(future)(identity)
              }
            )
          )
        )
      )
    )
}
