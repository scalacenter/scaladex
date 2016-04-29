package ch.epfl.scala.index
package cleanup

import scala.util._

import fastparse.all._
import fastparse.core.Parsed

sealed trait PreRelease
case class Milestone(digit: Int) extends PreRelease
case class ReleaseCandidate(digit: Int) extends PreRelease
case class OtherPreRelease(value: String) extends PreRelease

case class Version(
  major: Int, minor: Int, patch: Int,
  preRelease: Option[PreRelease],
  metadata: Option[String]
)

class VersionCleanup {
  val Alpha   = (CharIn('a' to 'z') | CharIn('A' to 'Z')).!
  val Digit   =  CharIn('0' to '9').!
  val Name    = (Alpha | Digit | "-".! ).rep.!
  
  val Major   = Digit.rep.!
  val Minor   = Digit.rep.!
  val Patch   = Digit.rep.!
  
  // http://semver.org/#spec-item-9
  val PreRelease =
    "-" ~ (
      (("M" | "m")               ~ Digit).map(d => Milestone(d.toInt)) |
      (("R" | "r") ~ ("C" | "c") ~ Digit).map(d => ReleaseCandidate(d.toInt)) |
      (Digit | Alpha | ".").rep.!.        map(s => OtherPreRelease(s))
    )

  // http://semver.org/#spec-item-10
  val MetaData = "+" ~ (Digit | Alpha | ".".!).rep.!
  
  val Version = Major ~ "." ~ Minor ~ ("." ~ Patch).? ~ PreRelease.? ~ MetaData.?
  
  val VersionSep     = P("_")
  val ScalaJsVersion = (VersionSep ~ "sjs" ~ Version)
  val ScalaVersion   = (VersionSep ~ Version)
  val PArtifact      = (Start ~ Name.! ~ ScalaJsVersion.? ~ ScalaVersion ~ End)
  
  // val poms = maven.Poms.get.collect{ case Success(p) => maven.PomConvert(p) }
  // val res = poms.map(_.artifactId).map(a => (a, PArtifact.parse(a)))
  // res.collect{
  //   case (a, f: Parsed.Failure) => (a, f)
  // }.take(100)
  
  // res.collect{
  //   case (a, Parsed.Success(b, r)) => (a, b, r)
  // }.size
}