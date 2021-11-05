package ch.epfl.scala.index
package server
package routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Assets {
  val routes: Route =
    pathPrefix("assets") {
      get(
        concat(
          path("lib" / Remaining)(path => getFromResource("lib/" + path)),
          path("img" / Remaining)(path => getFromResource("img/" + path)),
          path("css" / Remaining)(path => getFromResource("css/" + path)),
          path("js" / Remaining)(path => getFromResource("js/" + path)),
          path("client-opt.js")(
            getFromResource("client-opt.js")
          ),
          path("client-fastopt.js")(
            getFromResource("client-fastopt.js")
          ),
          path("client-opt.js.map")(
            getFromResource("client-opt.js.map")
          ),
          path("client-fastopt.js.map")(
            getFromResource("client-fastopt.js.map")
          ),
          path("opensearch.xml")(
            getFromResource("opensearch.xml")
          )
        )
      )
    }
}
