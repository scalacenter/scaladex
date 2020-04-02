package ch.epfl.scala.index.model

sealed trait PreRelease {
  def isSemantic: Boolean
}
case class ReleaseCandidate(rc: Long) extends PreRelease {
  def isSemantic: Boolean = true
}
case class Milestone(m: Long) extends PreRelease {
  def isSemantic: Boolean = true
}
case class OtherPreRelease(o: String) extends PreRelease {
  def isSemantic: Boolean = false
}

/**
 * Semantic version, separation of possible combinations
 * @param major the major version number
 * @param minor the minor version number
 * @param patch the path version number
 * @param patch2 the path version number (to support a.b.c.d)
 * @param preRelease the pre release name
 * @param metadata the release metadata
 */
case class SemanticVersion(
    major: Long,
    minor: Option[Long] = None,
    patch: Option[Long] = None,
    patch2: Option[Long] = None,
    preRelease: Option[PreRelease] = None,
    metadata: Option[String] = None
) extends Ordered[SemanticVersion] {

  def isSemantic: Boolean = {
    patch.isDefined &&
    patch2.isEmpty &&
    preRelease.forall(_.isSemantic)
  }

  override def toString: String = {
    val minorPart = minor.map(m => s".$m").getOrElse("")
    val patchPart = patch.map("." + _).getOrElse("")
    val patch2Part = patch2.map("." + _).getOrElse("")

    val preReleasePart = preRelease
      .map {
        case Milestone(d)        => "M" + d.toString
        case ReleaseCandidate(d) => "RC" + d.toString
        case OtherPreRelease(v)  => v.toString
      }
      .map("-" + _)
      .getOrElse("")

    val metadataPart = metadata.map("+" + _).getOrElse("")

    major + minorPart + patchPart + patch2Part + preReleasePart + metadataPart
  }

  def binary: SemanticVersion =
    if (preRelease.nonEmpty) this
    else forceBinary

  def forceBinary: SemanticVersion = SemanticVersion(major, minor)

  override def compare(that: SemanticVersion): PageIndex = {
    SemanticVersion.ordering.compare(this, that)
  }
}

object SemanticVersion extends Parsers {
  private implicit val preReleaseOrdering: Ordering[PreRelease] = Ordering.by {
    case ReleaseCandidate(rc) => (Some(rc), None, None)
    case Milestone(m) => (None, Some(m), None)
    case OtherPreRelease(pr) => (None, None, Some(pr))
  }

  implicit val ordering: Ordering[SemanticVersion] = Ordering.by {
    x => (x.major, x.minor, x.patch, x.patch2, x.preRelease)
  }

  def apply(major: Long, minor: Long): SemanticVersion = {
    SemanticVersion(major, Some(minor))
  }

  import fastparse._
  import fastparse.NoWhitespace._

  private def Number[_: P] = Digit.rep(1).!.map(_.toLong)
  private def Major[_: P] = Number

  // http://semver.org/#spec-item-9
  private def PreRelease[_: P]: P[PreRelease] =
    "-" ~ (
      (("M" | "m") ~ &(Digit) ~ Number).map(n => Milestone(n)) |
        (("R" | "r") ~ ("C" | "c") ~ &(Digit) ~ Number)
          .map(n => ReleaseCandidate(n)) |
        (Digit | Alpha | "." | "-").rep.!.map(s => OtherPreRelease(s))
    )

  // http://semver.org/#spec-item-10
  private def MetaData[_: P] = "+" ~ AnyChar.rep.!

  private def MinorP[_: P] = ("." ~ Number).? // not really valid SemVer
  private def PatchP[_: P] = ("." ~ Number).? // not really valid SemVer
  private def Patch2P[_: P] = ("." ~ Number).? // not really valid SemVer

  def Parser[_: P]: P[SemanticVersion] = {
    ("v".? ~ Major ~ MinorP ~ PatchP ~ Patch2P ~ PreRelease.? ~ MetaData.?)
      .map {
        case (major, minor, patch, patch2, preRelease, metadata) =>
          SemanticVersion(major, minor, patch, patch2, preRelease, metadata)
      }
  }

  private def FullParser[_: P] = Start ~ Parser ~ End
  def parse(version: String): Option[SemanticVersion] = apply(version)
  def apply(version: String): Option[SemanticVersion] = {
    fastparse.parse(version, x => FullParser(x)) match {
      case Parsed.Success(v, _) => Some(v)
      case _                    => None
    }
  }
}
