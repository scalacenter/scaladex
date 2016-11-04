package ch.epfl.scala.index.client
package rpc

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom

object AutowireClient extends autowire.Client[String, Reader, Writer] {

  import scala.scalajs.js.URIUtils.encodeURIComponent

  /** Reuse URL encoding and update implementation to reserve square brackets. */
  def encode(uri: String): String =
    encodeURIComponent(uri.filterNot(c => c == '[' || c == ']'))

  def buildQueryGet(args: Map[String, String]): String = {
    args.iterator.map {
      case (key, value) =>
        key + "=" + encode(value.toString.tail.init) // removes quotes around
    }.mkString("&")
  }

  /** Change this if you add more features to the client. */
  override def doCall(req: Request): Future[String] = {
    dom.ext.Ajax
      .get(
        url = "/api/" + req.path.last + "?" + buildQueryGet(req.args)
      )
      .map(_.responseText)
  }

  def read[T: Reader](p: String) = uread[T](p)
  def write[T: Writer](r: T) = uwrite(r)
}
