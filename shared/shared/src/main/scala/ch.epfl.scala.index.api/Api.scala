package ch.epfl.scala.index
package api

import play.api.libs.json._

object Autocompletion {
  implicit val formatAutocompletion: OFormat[Autocompletion] =
    Json.format[Autocompletion]
}

case class Autocompletion(organization: String,
                          repository: String,
                          description: String)
