package scaladex.server.route.api

import scala.concurrent.ExecutionContext

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.cors.scaladsl.CorsDirectives._
import endpoints4s.pekkohttp.server
import scaladex.core.api.AutocompletionResponse
import scaladex.core.api.SearchEndpoints
import scaladex.core.model.UserState
import scaladex.core.service.SearchEngine

class SearchApi(searchEngine: SearchEngine)(
    implicit val executionContext: ExecutionContext
) extends SearchEndpoints
    with server.Endpoints
    with server.JsonEntitiesFromSchemas {

  def route(user: Option[UserState]): Route =
    cors() {
      autocomplete.implementedByAsync { params =>
        val searchParams = params.withUser(user)
        for (projects <- searchEngine.autocomplete(searchParams, 5))
          yield projects.map { project =>
            AutocompletionResponse(
              project.organization.value,
              project.repository.value,
              project.githubInfo.flatMap(_.description).getOrElse("")
            )
          }
      }
    }
}
