package ch.epfl.scala.index

trait Api {
  def search(query: String): List[SimpleModel]
}