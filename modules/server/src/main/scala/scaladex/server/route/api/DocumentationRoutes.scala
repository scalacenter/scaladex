package scaladex.server.route.api

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import endpoints4s.pekkohttp.server
import endpoints4s.openapi.model.OpenApi

/**
 * Akka-Http routes serving the documentation of the public HTTP API of Scaladex
 */
object DocumentationRoutes extends server.Endpoints with server.JsonEntitiesFromEncodersAndDecoders {
  val routes: Route = cors() {
    endpoint(
      get(path / "api" / "open-api.json"),
      ok(jsonResponse[OpenApi])
    ).implementedBy(_ => ApiDocumentation.api)
  }
}
