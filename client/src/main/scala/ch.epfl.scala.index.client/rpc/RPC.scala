package ch.epfl.scala.index.client
package rpc

import ch.epfl.scala.index.api.{AutocompletionResponse, SearchRequest}
import org.scalajs.dom.ext.Ajax
import play.api.libs.json.{Json, Reads}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RPC {
  def autocomplete(
      request: SearchRequest
  ): Future[List[AutocompletionResponse]] = {
    val params = request.toHttpParams
      .map { case (key, value) => s"$key=$value" }
      .mkString("&")

    Ajax
      .get(s"/api/autocomplete?$params")
      .map(_.responseText)
      .map(read[List[AutocompletionResponse]](_))
  }

  private def read[T: Reads](p: String): T = Json.parse(p).as[T]
}
