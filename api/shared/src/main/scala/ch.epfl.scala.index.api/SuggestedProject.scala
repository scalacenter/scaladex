package ch.epfl.scala.index
package api

import play.api.libs.json._

object SuggestedProject {
  implicit val jsonFormat: OFormat[SuggestedProject] =
    Json.format[SuggestedProject]
}

case class SuggestedProject(
  organization: String,
  repository: String,
  description: String
)
