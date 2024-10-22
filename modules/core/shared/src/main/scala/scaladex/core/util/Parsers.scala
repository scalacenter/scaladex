package scaladex.core.util

import scala.util.Try

trait Parsers {

  import fastparse._

  def Alpha[A: P]: P[String] =
    CharPred(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).!

  def Digit[A: P]: P[String] = CharIn("0-9").!

  def Number[A: P]: P[Int] = {
    import NoWhitespace._
    Digit.rep(1).!.flatMap(i => tryP(i.toInt))
  }

  def tryP[T, A: P](p: => T): P[T] = Try(Pass(p)).getOrElse(Fail)

  def tryParse[T](input: ParserInputSource, parser: P[_] => P[T]): Option[T] =
    fastparse.parse(input, parser) match {
      case Parsed.Success(v, _) => Some(v)
      case _                    => None
    }
}

object Parsers extends Parsers
