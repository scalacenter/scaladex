package scaladex.server.route

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

object Assets {
  val routes: Route =
    pathPrefix("assets") {
      get(
        concat(
          pathPrefix("lib" / "swagger-ui")(redirect("/api/doc", StatusCodes.PermanentRedirect)),
          // be explicit on what we can get to avoid security leak
          pathPrefix("lib")(getFromResourceDirectory("lib")),
          pathPrefix("img")(getFromResourceDirectory("img")),
          pathPrefix("css")(getFromResourceDirectory("css")),
          pathPrefix("js")(getFromResourceDirectory("js")),
          path("webclient-opt.js")(
            getFromResource("webclient-opt.js")
          ),
          path("webclient-fastopt.js")(
            getFromResource("webclient-fastopt.js")
          ),
          path("webclient-opt.js.map")(
            getFromResource("webclient-opt.js.map")
          ),
          path("webclient-fastopt.js.map")(
            getFromResource("webclient-fastopt.js.map")
          ),
          path("opensearch.xml")(
            getFromResource("opensearch.xml")
          )
        )
      )
    }
}
