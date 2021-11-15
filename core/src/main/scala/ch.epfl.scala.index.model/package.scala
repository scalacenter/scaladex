package ch.epfl.scala.index

package object model {
  def Descending[T: Ordering]: Ordering[T] = implicitly[Ordering[T]].reverse
}
