package ch.epfl.scala.index
package api

import scala.concurrent.Future

import play.api.libs.json._

trait Api {
  def autocomplete(q: String): Future[List[Autocompletion]]
}

object Autocompletion {
  implicit val formatAutocompletion: OFormat[Autocompletion] =
    Json.format[Autocompletion]
}

case class Autocompletion(organization: String,
                          repository: String,
                          description: String)
