package ch.epfl.scala.index

package object model {
  def Descending[T: Ordering] = implicitly[Ordering[T]].reverse
  type PageIndex = Int
}
