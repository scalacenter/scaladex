package scaladex.server.route

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

object Assets {
  val routes: Route =
    pathPrefix("assets") {
      get(
        concat(
          path("lib" / Remaining)(path => getFromResource("lib/" + path)),
          path("img" / Remaining)(path => getFromResource("img/" + path)),
          path("css" / Remaining)(path => getFromResource("css/" + path)),
          path("js" / Remaining)(path => getFromResource("js/" + path)),
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
