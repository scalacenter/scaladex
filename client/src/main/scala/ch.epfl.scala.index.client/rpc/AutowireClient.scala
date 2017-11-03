package ch.epfl.scala.index.client
package rpc

import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import play.api.libs.json.{JsObject, Json, Reads, Writes}

object AutowireClient extends autowire.Client[String, Reads, Writes] {

  import scala.scalajs.js.URIUtils.encodeURIComponent

  /** Reuse URL encoding and update implementation to reserve square brackets. */
  def encode(uri: String): String =
    encodeURIComponent(uri.filterNot(c => c == '[' || c == ']'))

  def buildQueryGet(args: Map[String, String]): String = {
    args.iterator
      .map {
        case (key, value) =>
          key + "=" + encode(value.toString.tail.init) // removes quotes around
      }
      .mkString("&")
  }

  /** Change this if you add more features to the client. */
  override def doCall(req: Request): Future[String] = {
    dom.ext.Ajax
      .get(
        url = "/api/" + req.path.last + "?" + buildQueryGet(req.args)
      )
      .map(_.responseText)
  }

  def read[T: Reads](p: String): T = Json.parse(p).as[T]

  def write[T: Writes](r: T): String = Json.stringify(Json.toJson(r))
}
