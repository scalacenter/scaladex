package ch.epfl.scala.index.client
package rpc

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import ch.epfl.scala.index.api.AutocompletionResponse
import ch.epfl.scala.index.api.SearchRequest
import org.scalajs.dom.ext.Ajax
import play.api.libs.json.Json
import play.api.libs.json.Reads

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
