package scaladex.server.route.api

import endpoints4s.openapi
import endpoints4s.openapi.model.Info
import endpoints4s.openapi.model.OpenApi
import scaladex.core.api.SearchEndpoints
import scaladex.core.api.artifact.ArtifactEndpoints

/**
 * Documentation of the public HTTP API of Scaladex
 */
object ApiDocumentation
    extends SearchEndpoints
    with ArtifactEndpoints
    with openapi.Endpoints
    with openapi.JsonEntitiesFromSchemas {

  val api: OpenApi = openApi(
    Info(
      title = "Scaladex API",
      version = "0.1.0"
    )
  )(autocomplete, artifact)
}
