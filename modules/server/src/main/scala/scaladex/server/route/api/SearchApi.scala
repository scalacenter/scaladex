package scaladex.server.route.api
import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import endpoints4s.akkahttp.server
import scaladex.core.api.AutocompletionParams
import scaladex.core.api.AutocompletionResponse
import scaladex.core.api.SearchEndpoints
import scaladex.core.model.search.SearchParams
import scaladex.core.service.SearchEngine
import scaladex.server.GithubUserSession

class SearchApi(searchEngine: SearchEngine, session: GithubUserSession)(
    implicit val executionContext: ExecutionContext
) extends SearchEndpoints
    with server.Endpoints
    with server.JsonEntitiesFromSchemas {
  import session.implicits._

  val routes: Route =
    cors() {
      autocomplete.implementedByAsync { params =>
        for (projects <- searchEngine.autocomplete(params, 5))
          yield projects.map { project =>
            AutocompletionResponse(
              project.organization.value,
              project.repository.value,
              project.githubInfo.flatMap(_.description).getOrElse("")
            )
          }
      }
    }

  type WithSession = SearchParams

  def withOptionalSession(request: Request[AutocompletionParams]): Request[SearchParams] =
    new Request[SearchParams] {
      val directive: Directive1[SearchParams] = Directive[Tuple1[SearchParams]] { f =>
        optionalSession(refreshable, usingCookies) { maybeUserId =>
          val futureMaybeUser = maybeUserId.traverse(session.getUser).map(_.flatten)
          val futureRoute = futureMaybeUser.map { maybeUser =>
            request.directive(request => f(Tuple1(request.withUser(maybeUser))))
          }
          context => futureRoute.flatMap(_.apply(context))
        }
      }
      def uri(params: SearchParams): Uri = request.uri(params.toAutocomplete)
    }
}
