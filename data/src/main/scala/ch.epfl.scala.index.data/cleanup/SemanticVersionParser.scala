package ch.epfl.scala.index
package data
package cleanup

import ch.epfl.scala.index.model.release._
import fastparse.all._
import fastparse.core.Parsed

object SemanticVersionParser {
  val Parser = {
    
    val Number  = Digit.rep(1).!.map(_.toLong)  
    val Major = Number
    
    // http://semver.org/#spec-item-9
    val PreRelease: P[PreRelease] =
      "-" ~ (
        (("M" | "m") ~ &(Digit)               ~ Number).map(n => Milestone(n)) |
        (("R" | "r") ~ ("C" | "c") ~ &(Digit) ~ Number).map(n => ReleaseCandidate(n)) |
        (Digit | Alpha | "." | "-").rep.!.              map(s => OtherPreRelease(s))
      )

    // http://semver.org/#spec-item-10
    val MetaData = "+" ~ AnyChar.rep.!
    
    val MinorPart = ("." ~ Number).?.map(_.getOrElse(0L)) // not really valid SemVer
    val PatchPart = ("." ~ Number).?                      // not really valid SemVer

    ("v".? ~ Major ~ MinorPart ~ PatchPart ~ PreRelease.? ~ MetaData.?).map{
      case (major, minor, patch, preRelease, metadata) =>
        SemanticVersion(major, minor, patch, preRelease, metadata)
    }
  }
  private val FullParser = Start ~ Parser ~ End
  def apply(version: String): Option[SemanticVersion] = {
    FullParser.parse(version) match {
      case Parsed.Success(v, _) => Some(v)
      case _ => None
    }
  }
}