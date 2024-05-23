package scaladex.core.model

import fastparse.NoWhitespace._
import fastparse._
import scaladex.core.util.Parsers._

/**
 * Semantic version, separation of possible combinations
 *
 * @param major the major version number
 * @param minor the minor version number
 * @param patch the patch version number
 * @param patch2 the second patch version number (to support a.b.c.d)
 * @param preRelease the pre release name
 * @param metadata the release metadata
 */
case class SemanticVersion(
    major: Int,
    minor: Option[Int] = None,
    patch: Option[Int] = None,
    patch2: Option[Int] = None,
    preRelease: Option[PreRelease] = None,
    metadata: Option[String] = None
) extends Ordered[SemanticVersion] {

  def isSemantic: Boolean =
    patch.isDefined &&
      patch2.isEmpty &&
      preRelease.forall(_.isSemantic)

  def isPreRelease: Boolean =
    preRelease.isDefined || metadata.isDefined

  def isStable: Boolean =
    isSemantic && !isPreRelease

  override def toString: String = this match {
    case MajorVersion(major) => s"$major.x"
    case _                   => encode
  }

  def encode: String = {
    val minorPart = minor.map(m => s".$m").getOrElse("")
    val patchPart = patch.map(p => s".$p").getOrElse("")
    val patch2Part = patch2.map(p2 => s".$p2").getOrElse("")
    val preReleasePart = preRelease.map(r => s"-$r").getOrElse("")
    val metadataPart = metadata.map(m => s"+$m").getOrElse("")
    s"$major$minorPart$patchPart$patch2Part$preReleasePart$metadataPart"
  }

  override def compare(that: SemanticVersion): Int =
    SemanticVersion.ordering.compare(this, that)
}

object MajorVersion {
  def apply(major: Int): SemanticVersion = SemanticVersion(major)

  def unapply(version: SemanticVersion): Option[Int] = version match {
    case SemanticVersion(major, None, None, None, None, None) => Some(major)
    case _                                                    => None
  }
}

object MinorVersion {
  def apply(major: Int, minor: Int): SemanticVersion = SemanticVersion(major, Some(minor))

  def unapply(version: SemanticVersion): Option[(Int, Int)] = version match {
    case SemanticVersion(major, Some(minor), None, None, None, None) => Some((major, minor))
    case _                                                           => None
  }
}

object PatchVersion {
  def apply(major: Int, minor: Int, patch: Int): SemanticVersion = SemanticVersion(major, Some(minor), Some(patch))

  def unapply(version: SemanticVersion): Option[(Int, Int, Int)] = version match {
    case SemanticVersion(major, Some(minor), Some(patch), None, None, None) => Some((major, minor, patch))
    case _                                                                  => None
  }
}

object PreReleaseVersion {
  def apply(major: Int, minor: Int, patch: Int, preRelease: PreRelease): SemanticVersion =
    SemanticVersion(major, Some(minor), Some(patch), preRelease = Some(preRelease))

  def unapply(version: SemanticVersion): Option[(Int, Int, Int, PreRelease)] = version match {
    case SemanticVersion(major, Some(minor), Some(patch), None, Some(preRelease), None) =>
      Some((major, minor, patch, preRelease))
    case _ => None
  }
}

object SemanticVersion {
  implicit val ordering: Ordering[SemanticVersion] = Ordering.by {
    case SemanticVersion(major, minor, patch, patch2, preRelease, metadata) =>
      (
        major,
        minor.getOrElse(Int.MaxValue),
        patch.getOrElse(Int.MaxValue),
        patch2.getOrElse(Int.MaxValue),
        preRelease,
        metadata
      )
  }

  // We prefer the latest stable artifact.
  val PreferStable: Ordering[SemanticVersion] =
    Ordering.by[SemanticVersion, Boolean](_.isStable).orElse(ordering)

  private def MajorP[A: P]: P[Int] = Number

  // http://semver.org/#spec-item-10
  private def MetaDataP[A: P] = "+" ~ AnyChar.rep.!

  private def MinorP[A: P] = ("." ~ Number).? // not really valid SemVer
  private def PatchP[A: P] = ("." ~ Number).? // not really valid SemVer
  private def Patch2P[A: P] = ("." ~ Number).? // not really valid SemVer

  def Parser[A: P]: P[SemanticVersion] =
    ("v".? ~ MajorP ~ MinorP ~ PatchP ~ Patch2P ~ ("-" ~ PreRelease.Parser).? ~ MetaDataP.?)
      .map {
        case (major, minor, patch, patch2, preRelease, metadata) =>
          SemanticVersion(major, minor, patch, patch2, preRelease, metadata)
      }

  private def FullParser[A: P]: P[SemanticVersion] = Start ~ Parser ~ End

  def parse(version: String): Option[SemanticVersion] =
    fastparse.parse(version, x => FullParser(x)) match {
      case Parsed.Success(v, _) => Some(v)
      case _                    => None
    }
}
