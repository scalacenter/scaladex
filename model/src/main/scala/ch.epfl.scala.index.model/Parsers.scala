package ch.epfl.scala.index.model

trait Parsers {
  import fastparse._

  protected def Alpha[_: P] =
    CharPred(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).!
  protected def Digit[_: P] = CharIn("0123456789").!
}
