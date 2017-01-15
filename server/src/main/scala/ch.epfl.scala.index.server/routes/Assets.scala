package ch.epfl.scala.index
package server
package routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Assets {

  val routes =
    get {
      concat(
        assetDirectory("lib"),
        assetDirectory("img"),
        assetDirectory("css"),
        assetDirectory("js"),
        asset("client-opt.js"),
        asset("client-opt.js.map"),
        asset("client-fastopt.js"),
        asset("client-fastopt.js.map"),
        asset("client-jsdeps.js")
      )
    }

  private def asset(name: String): Route = {
    path("assets" / name) {
      getFromResource(name)
    }
  }

  private def assetDirectory(directory: String): Route = {
    path("assets" / directory / Remaining) { path ⇒
      getFromResource(s"$directory/" + path)
    }
  }
}
