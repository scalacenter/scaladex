package scaladex.core.model

import scaladex.core.util.Parsers

sealed trait PreRelease {
  def isSemantic: Boolean
}
case class ReleaseCandidate(rc: Int) extends PreRelease {
  def isSemantic: Boolean = true
  override def toString: String = s"RC$rc"
}
case class Milestone(m: Int) extends PreRelease {
  def isSemantic: Boolean = true
  override def toString: String = s"M$m"
}
case class OtherPreRelease(o: String) extends PreRelease {
  def isSemantic: Boolean = false
  override def toString: String = o
}

object PreRelease extends Parsers {
  implicit val preReleaseOrdering: Ordering[Option[PreRelease]] =
    Ordering.by {
      case None                       => (3, None, None)
      case Some(ReleaseCandidate(rc)) => (2, Some(rc), None)
      case Some(Milestone(m))         => (1, Some(m), None)
      case Some(OtherPreRelease(pr))  => (0, None, Some(pr))
    }

  import fastparse.NoWhitespace._
  import fastparse._

  // http://semver.org/#spec-item-9
  def Parser[A: P]: P[PreRelease] =
    (("M" | "m") ~ &(Digit) ~ Number).map(n => Milestone(n)) |
      (("R" | "r") ~ ("C" | "c") ~ &(Digit) ~ Number)
        .map(n => ReleaseCandidate(n)) |
      (Digit | Alpha | "." | "-").rep.!.map(s => OtherPreRelease(s))
}
