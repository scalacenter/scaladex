package ch.epfl.scala.index
package server

import akka.http.scaladsl.model._

object Template {
  val index = {
    import scalatags.Text.all._
    import scalatags.Text.tags2.title

    "<!DOCTYPE html>" +
    html(
      head(
        title("Scaladex"),
        base(href:="/"),
        meta(charset:="utf-8")
      ),
      body(
        script(src:="/assets/webapp-jsdeps.js"),
        script(src:="/assets/webapp-fastopt.js"),
        script("ch.epfl.scala.index.client.Client().main()")
      )
    )
  }
  val home = HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, index))
}