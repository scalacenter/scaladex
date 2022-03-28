package scaladex.server.route.api

import endpoints4s.openapi
import endpoints4s.openapi.model.Info
import endpoints4s.openapi.model.OpenApi
import scaladex.core.api.AutocompletionParams
import scaladex.core.api.SearchEndpoints

/**
 * Documentation of the public HTTP API of Scaladex
 */
object ApiDocumentation extends SearchEndpoints with openapi.Endpoints with openapi.JsonEntitiesFromSchemas {

  val api: OpenApi = openApi(
    Info(
      title = "Scaladex API",
      version = "0.1.0"
    )
  )(autocomplete)

  // We don’t document the session
  def withOptionalSession(request: Request[AutocompletionParams]): Request[AutocompletionParams] = request
  type WithSession = AutocompletionParams

}
