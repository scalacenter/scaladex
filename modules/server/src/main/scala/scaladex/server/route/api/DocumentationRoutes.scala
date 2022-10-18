package scaladex.server.route.api

import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import endpoints4s.akkahttp.server
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
