package scaladex.server.route.api

import endpoints4s.openapi.model.OpenApi
import endpoints4s.pekkohttp.server
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import org.apache.pekko.http.scaladsl.server.Directives.concat
import org.apache.pekko.http.scaladsl.server.Route

/**
 * Akka-Http routes serving the documentation of the public HTTP API of Scaladex
 */
object DocumentationRoute extends server.Endpoints with server.JsonEntitiesFromEncodersAndDecoders {
  val route: Route = cors() {
    concat(
      endpoint(
        get(path / "api" / "open-api.json"),
        ok(jsonResponse[OpenApi])
      ).implementedBy(_ => ApiDocumentation.apiV0),
      endpoint(
        get(path / "api" / "v1" / "open-api.json"),
        ok(jsonResponse[OpenApi])
      ).implementedBy(_ => ApiDocumentation.apiV1)
    )
  }
}
