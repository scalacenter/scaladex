package scaladex.core.model

import scaladex.core.util.Parsers.*

import fastparse.*
import fastparse.NoWhitespace.*

sealed trait Version extends Ordered[Version]:
  def value: String
  def isSemantic: Boolean

  /** A stable semantic version, such as 2.0.3, 2.3 or 2
    */
  def isStable: Boolean

  /** A pre-release semantic version, such as 2.3.0-RC1
    */
  def isPreRelease: Boolean

  override def compare(that: Version): Int = Version.ordering.compare(this, that)
  override def toString: String = this match
    case Version.Major(major) => s"$major.x"
    case _ => value
end Version

object Version:
  given ordering: Ordering[Version] = Ordering.by:
    case Custom(value) => (Int.MinValue, Int.MinValue, Int.MinValue, Int.MinValue, None, Some(value))
    case SemanticLike(maj, min, p, p2, pr, m) =>
      (maj, min.getOrElse(Int.MaxValue), p.getOrElse(Int.MaxValue), p2.getOrElse(Int.MaxValue), pr, m)

  // We prefer the latest stable artifact.
  val PreferStable: Ordering[Version] =
    Ordering.by((v: Version) => v.isStable).orElse(ordering)

  final case class SemanticLike(
      major: Int,
      minor: Option[Int] = None,
      patch: Option[Int] = None,
      patch2: Option[Int] = None,
      preRelease: Option[PreRelease] = None,
      metadata: Option[String] = None
  ) extends Version:
    override def isSemantic: Boolean = patch2.isEmpty && preRelease.forall(_.isSemantic)

    override def isPreRelease: Boolean = preRelease.isDefined || metadata.isDefined

    override def isStable: Boolean = isSemantic && !isPreRelease

    override def value: String =
      val minorPart = minor.map(m => s".$m").getOrElse("")
      val patchPart = patch.map(p => s".$p").getOrElse("")
      val patch2Part = patch2.map(p2 => s".$p2").getOrElse("")
      val preReleasePart = preRelease.map(r => s"-$r").getOrElse("")
      val metadataPart = metadata.map(m => s"+$m").getOrElse("")
      s"$major$minorPart$patchPart$patch2Part$preReleasePart$metadataPart"
  end SemanticLike

  final case class Custom(value: String) extends Version:
    override def isSemantic: Boolean = false
    override def isPreRelease: Boolean = false
    override def isStable: Boolean = false

  def apply(maj: Int): Version = SemanticLike(maj)
  def apply(maj: Int, min: Int): Version = SemanticLike(maj, Some(min))
  def apply(maj: Int, min: Int, patch: Int): Version = Version.SemanticLike(maj, Some(min), Some(patch))
  def apply(maj: Int, min: Int, patch: Int, preRelease: PreRelease): SemanticLike =
    Version.SemanticLike(maj, Some(min), Some(patch), None, Some(preRelease))

  object Major:
    def unapply(version: SemanticLike): Option[Int] = version match
      case SemanticLike(major, None, None, None, None, None) => Some(major)
      case _ => None

  object Minor:
    def unapply(version: SemanticLike): Option[(Int, Int)] = version match
      case SemanticLike(maj, Some(min), None, None, None, None) => Some((maj, min))
      case _ => None

  object Patch:
    def unapply(version: SemanticLike): Option[(Int, Int, Int)] = version match
      case SemanticLike(major, Some(min), Some(patch), None, None, None) => Some((major, min, patch))
      case _ => None

  private def MajorP[A: P]: P[Int] = Number

  // http://semver.org/#spec-item-10
  private def MetaDataP[A: P] = "+" ~ AnyChar.rep.!

  private def MinorP[A: P] = ("." ~ Number).? // not really valid SemVer
  private def PatchP[A: P] = ("." ~ Number).? // not really valid SemVer
  private def Patch2P[A: P] = ("." ~ Number).? // not really valid SemVer

  def SemanticParser[A: P]: P[Version.SemanticLike] =
    ("v".? ~ MajorP ~ MinorP ~ PatchP ~ Patch2P ~ ("-" ~ PreRelease.Parser).? ~ MetaDataP.?)
      .map { case (maj, min, p, p2, pr, m) => Version.SemanticLike(maj, min, p, p2, pr, m) }

  private def Parser[A: P]: P[Version.SemanticLike] = Start ~ SemanticParser ~ End

  def parseSemantically(version: String): Option[Version.SemanticLike] = tryParse(version, x => Parser(x))

  def apply(version: String): Version = parseSemantically(version).getOrElse(Version.Custom(version))
end Version
