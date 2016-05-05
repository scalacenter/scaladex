package ch.epfl.scala.index
package cleanup

import scala.util._

import fastparse.all._
import fastparse.core.Parsed

sealed trait PreRelease
case class Milestone(digit: Long) extends PreRelease
case class ReleaseCandidate(digit: Long) extends PreRelease
case class OtherPreRelease(value: String) extends PreRelease

case class SemanticVersion(
  major: Long, minor: Long = 0, patch: Long = 0,
  preRelease: Option[PreRelease] = None,
  metadata: Option[String] = None
) {
  override def toString = {
    val preReleasePart = preRelease.map{
      case Milestone(d) => "M" + d.toString
      case ReleaseCandidate(d) => "RC" + d.toString
      case OtherPreRelease(v) => v.toString
    }.map(v => "-" + v).getOrElse("")    
    val metadataPart = metadata.map(md => "+" + md).getOrElse("")
    s"$major.$minor.$patch$preReleasePart$metadataPart"
  }
}

object SemanticVersion {
  implicit def ordering = new Ordering[SemanticVersion] {
    val LT = -1
    val GT =  1
    val EQ =  0
    
    val lcmp = implicitly[Ordering[Long]]
    val scmp = implicitly[Ordering[String]]
    val cmp = implicitly[Ordering[(Long, Long, Long)]]

    def compare(v1: SemanticVersion, v2: SemanticVersion): Int = {     
      def tupled(v: SemanticVersion) = {
        import v._
        (major, minor, patch)
      }
      
      val tv1 = tupled(v1)
      val tv2 = tupled(v2)
      
      def preCmp(pr1: Option[PreRelease], pr2: Option[PreRelease]): Int = {
        (pr1, pr2) match {
          case (None, None)                                               => EQ
          case (None, Some(_))                                            => GT
          case (Some(_), None)                                            => LT
          case (Some(ReleaseCandidate(rc1)), Some(ReleaseCandidate(rc2))) => lcmp.compare(rc1, rc2)
          case (Some(ReleaseCandidate(_))  , Some(Milestone(_)))          => GT
          case (Some(Milestone(_))         , Some(ReleaseCandidate(_)))   => LT
          case (Some(Milestone(m1))        , Some(Milestone(m2)))         => lcmp.compare(m1, m2)
          case (Some(OtherPreRelease(pr1)) , Some(OtherPreRelease(pr2)))  => scmp.compare(pr1, pr2)
          case (Some(OtherPreRelease(_))   , Some(Milestone(_)))          => LT
          case (Some(OtherPreRelease(_))   , Some(ReleaseCandidate(_)))   => LT
          case (Some(_)                    , Some(OtherPreRelease(_)))    => GT
        }
      }
      
      // Milestone < Release Candidate < Released
      if(cmp.equiv(tv1, tv2)) preCmp(v1.preRelease, v2.preRelease) 
      else cmp.compare(tv1, tv2)      
    }
  }
  private val SemanticVersioningParser = {
    val Alpha   = (CharIn('a' to 'z') | CharIn('A' to 'Z')).!
    val Digit   =  CharIn('0' to '9').!
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
    val MetaData = "+" ~ (Digit | Alpha | ".".!).rep.!
    
    val MinorPart = ("." ~ Number).?.map(_.getOrElse(0L)) // not really valid SemVer
    val PatchPart = ("." ~ Number).?.map(_.getOrElse(0L)) // not really valid SemVer

    (Start ~ "v".? ~ Major ~ MinorPart ~ PatchPart ~ PreRelease.? ~ MetaData.? ~ End).map{
      case (major, minor, patch, preRelease, metadata) =>
        SemanticVersion(major, minor, patch, preRelease, metadata)
    }
  }
  def apply(version: String): Option[SemanticVersion] = {
    SemanticVersioningParser.parse(version) match {
      case Parsed.Success(v, _) => Some(v)
      case _ => None
    }
  }
}