package ch.epfl.scala.index
package api

import scala.concurrent.Future

trait Api {
  def autocomplete(q: String): Future[List[Autocompletion]]
}

case class Autocompletion(organization: String,
                          repository: String,
                          description: String)
