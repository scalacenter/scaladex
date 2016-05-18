package ch.epfl.scala.index

package object data {
  def Descending[T : Ordering] = implicitly[Ordering[T]].reverse
}