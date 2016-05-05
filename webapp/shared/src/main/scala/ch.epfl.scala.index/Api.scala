package ch.epfl.scala.index

import scala.concurrent.Future

trait Api {
  def find(q: String): Future[(Long, List[Project])]
}