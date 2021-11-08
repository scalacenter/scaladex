package ch.epfl.scala.index.model

trait Parsers {
  import fastparse._

  protected def Alpha[_: P]: P[String] =
    CharPred(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).!
  protected def Digit[_: P]: P[String] = CharIn("0123456789").!

  protected def Number[_: P]: P[Int] = {
    import NoWhitespace._
    Digit.rep(1).!.map(_.toInt)
  }

  protected def tryParse[T](input: ParserInputSource, parser: P[_] => P[T]): Option[T] =
    fastparse.parse(input, parser) match {
      case Parsed.Success(v, _) => Some(v)
      case _                    => None
    }
}
