package ch.epfl.scala.index
package api

import play.api.libs.json.Format
import play.api.libs.json.Json

object AutocompletionResponse {
  implicit val jsonFormat: Format[AutocompletionResponse] =
    Json.format[AutocompletionResponse]
}

case class AutocompletionResponse(
    organization: String,
    repository: String,
    description: String
)
