package ch.epfl.scala.index
package api

import ch.epfl.scala.index.api.Api.Autocompletion
import ch.epfl.scala.index.model.Project.Reference

import scala.concurrent.Future

trait Api {
  def search(q: String): Future[List[Autocompletion]]
}

object Api {
  type Autocompletion = (Reference, String)
}

