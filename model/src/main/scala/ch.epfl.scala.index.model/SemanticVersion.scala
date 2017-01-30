package ch.epfl.scala.index.model

sealed trait PreRelease
case class ReleaseCandidate(rc: Long) extends PreRelease
case class Milestone(m: Long) extends PreRelease
case class OtherPreRelease(o: String) extends PreRelease

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
    minor: Long = 0,
    patch: Option[Long] = None,
    patch2: Option[Long] = None,
    preRelease: Option[PreRelease] = None,
    metadata: Option[String] = None
) extends Ordered[SemanticVersion] {

  override def toString: String = {
    val patchPart = patch.map("." + _).getOrElse("")
    val patch2Part = patch2.map("." + _).getOrElse("")

    val preReleasePart = preRelease.map {
      case Milestone(d) => "M" + d.toString
      case ReleaseCandidate(d) => "RC" + d.toString
      case OtherPreRelease(v) => v.toString
    }.map("-" + _).getOrElse("")

    val metadataPart = metadata.map("+" + _).getOrElse("")

    major + "." + minor + patchPart + patch2Part + preReleasePart + metadataPart
  }

  def binary: SemanticVersion =
    if (preRelease.nonEmpty) this
    else forceBinary

  def forceBinary: SemanticVersion = SemanticVersion(major, minor)

  private final val LT = -1
  private final val GT = 1
  private final val EQ = 0

  private final val lcmp = implicitly[Ordering[Long]]
  private final val scmp = implicitly[Ordering[String]]
  private final val cmp = implicitly[Ordering[(Long, Long, Option[Long], Option[Long])]]

  override def compare(that: SemanticVersion): Int = {
    val v1 = this
    val v2 = that

    def tupled(v: SemanticVersion) = (v.major, v.minor, v.patch, v.patch2)
    val tv1 = tupled(v1)
    val tv2 = tupled(v2)

    def preCmp(pr1: Option[PreRelease], pr2: Option[PreRelease]): Int = {
      // format: off
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
        case _                                                          => EQ
      }
      // format: on
    }

    // Milestone < Release Candidate < Released
    if (cmp.equiv(tv1, tv2)) preCmp(v1.preRelease, v2.preRelease)
    else cmp.compare(tv1, tv2)
  }
}

object SemanticVersion extends Parsers {
  import fastparse.all._
  import fastparse.core.Parsed

  implicit def ordering = new Ordering[SemanticVersion] {
    def compare(v1: SemanticVersion, v2: SemanticVersion): Int =
      v1.compare(v2)
  }

  val Parser = {
    val Number = Digit.rep(1).!.map(_.toLong)
    val Major = Number

    // http://semver.org/#spec-item-9
    val PreRelease: P[PreRelease] =
      "-" ~ (
        (("M" | "m") ~ &(Digit) ~ Number).map(n => Milestone(n)) |
          (("R" | "r") ~ ("C" | "c") ~ &(Digit) ~ Number).map(n => ReleaseCandidate(n)) |
          (Digit | Alpha | "." | "-").rep.!.map(s => OtherPreRelease(s))
      )

    // http://semver.org/#spec-item-10
    val MetaData = "+" ~ AnyChar.rep.!

    val MinorP = ("." ~ Number).?.map(_.getOrElse(0L)) // not really valid SemVer
    val PatchP = ("." ~ Number).? // not really valid SemVer
    val Patch2P = ("." ~ Number).? // not really valid SemVer

    ("v".? ~ Major ~ MinorP ~ PatchP ~ Patch2P ~ PreRelease.? ~ MetaData.?).map {
      case (major, minor, patch, patch2, preRelease, metadata) =>
        SemanticVersion(major, minor, patch, patch2, preRelease, metadata)
    }
  }
  private val FullParser = Start ~ Parser ~ End
  def parse(version: String): Option[SemanticVersion] = apply(version)
  def apply(version: String): Option[SemanticVersion] = {
    FullParser.parse(version) match {
      case Parsed.Success(v, _) => Some(v)
      case _ => None
    }
  }
}
