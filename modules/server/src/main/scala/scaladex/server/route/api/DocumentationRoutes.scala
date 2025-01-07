package scaladex.server.route.api

import endpoints4s.Encoder
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

/** Akka-Http routes serving the documentation of the public HTTP API of Scaladex
  */
object DocumentationRoute:
  implicit def marshallerFromEncoder[T](implicit encoder: Encoder[T, String]): ToEntityMarshaller[T] =
    Marshaller
      .stringMarshaller(MediaTypes.`application/json`)
      .compose(c => encoder.encode(c))

  val route: Route = cors() {
    get {
      concat(
        pathPrefix("api" / "doc")(
          pathEnd(redirect("/api/doc/", StatusCodes.PermanentRedirect)) ~
            pathSingleSlash(getFromResource("lib/swagger-ui/index.html")) ~
            // override default swagger-initializer
            path("swagger-initializer.js")(getFromResource("lib/swagger-initializer.js")) ~
            getFromResourceDirectory("lib/swagger-ui")
        ),
        path("api" / "open-api.json")(complete(StatusCodes.OK, ApiDocumentation.apiV0)),
        path("api" / "v1" / "open-api.json")(complete(StatusCodes.OK, ApiDocumentation.apiV1))
      )
    }
  }
end DocumentationRoute
