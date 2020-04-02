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
  private implicit val preReleaseOrdering: Ordering[Option[PreRelease]] =
    Ordering.by {
      case None                       => (3, None, None)
      case Some(ReleaseCandidate(rc)) => (2, Some(rc), None)
      case Some(Milestone(m))         => (1, Some(m), None)
      case Some(OtherPreRelease(pr))  => (0, None, Some(pr))
    }

  implicit def ordering: Ordering[SemanticVersion] = Ordering.by { x =>
    (x.major, x.minor, x.patch, x.patch2, x.preRelease)
  }

  def apply(major: Long, minor: Long): SemanticVersion = {
    SemanticVersion(major, Some(minor))
  }

  def apply(major: Long, minor: Long, patch: Long): SemanticVersion = {
    SemanticVersion(major, Some(minor), Some(patch))
  }

  def apply(major: Long,
            minor: Long,
            patch: Long,
            patch2: Long): SemanticVersion = {
    SemanticVersion(major, Some(minor), Some(patch), Some(patch2))
  }

  def apply(major: Long,
            minor: Long,
            patch: Long,
            preRelease: PreRelease): SemanticVersion = {
    SemanticVersion(major, Some(minor), Some(patch), None, Some(preRelease))
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
