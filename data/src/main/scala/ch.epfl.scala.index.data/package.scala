package ch.epfl.scala.index

import fastparse.all._

package object data {
  def Descending[T : Ordering] = implicitly[Ordering[T]].reverse

  val Alpha = (CharIn('a' to 'z') | CharIn('A' to 'Z')).!
  val Digit =  CharIn('0' to '9').!
}