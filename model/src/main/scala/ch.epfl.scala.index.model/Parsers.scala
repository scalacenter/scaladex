package ch.epfl.scala.index.model


trait Parsers {
  import fastparse.all._

  protected val Alpha = (CharIn('a' to 'z') | CharIn('A' to 'Z')).!
  protected val Digit =  CharIn('0' to '9').!
}