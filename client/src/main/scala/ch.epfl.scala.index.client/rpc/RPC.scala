package ch.epfl.scala.index.client
package rpc

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.encodeURIComponent
import org.scalajs.dom
import play.api.libs.json.{Json, Reads, Writes}

import ch.epfl.scala.index.api.Autocompletion

object RPC {
  def autocomplete(q: String): Future[List[Autocompletion]] = {
    val args = Map("q" -> write(q))
    doCall("autocomplete", args).map(read[List[Autocompletion]](_))
  }

  /** Reuse URL encoding and update implementation to reserve square brackets. */
  private def encode(uri: String): String =
    encodeURIComponent(uri.filterNot(c => c == '[' || c == ']'))

  private def buildQueryGet(args: Map[String, String]): String = {
    args.iterator
      .map {
        case (key, value) =>
          key + "=" + encode(value.toString.tail.init) // removes quotes around
      }
      .mkString("&")
  }

  /** Change this if you add more features to the client. */
  private def doCall(path: String, args: Map[String, String]): Future[String] = {
    dom.ext.Ajax
      .get(
        url = "/api/" + path + "?" + buildQueryGet(args)
      )
      .map(_.responseText)
  }

  private def read[T: Reads](p: String): T = Json.parse(p).as[T]

  private def write[T: Writes](r: T): String = Json.stringify(Json.toJson(r))
}
