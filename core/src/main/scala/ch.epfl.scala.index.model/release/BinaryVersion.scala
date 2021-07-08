package ch.epfl.scala.index.model.release

import ch.epfl.scala.index.model.{Parsers, PreRelease}
import ch.epfl.scala.index.model.ReleaseCandidate

sealed trait BinaryVersion extends Ordered[BinaryVersion] {
  override def compare(that: BinaryVersion): Int = {
    BinaryVersion.ordering.compare(this, that)
  }
}

final case class MajorBinary(major: Int) extends BinaryVersion {
  override def toString: String = s"$major.x"
}

final case class MinorBinary(major: Int, minor: Int) extends BinaryVersion {
  override def toString: String = s"$major.$minor"
}

final case class PatchBinary(major: Int, minor: Int, patch: Int)
    extends BinaryVersion {
  override def toString: String = s"$major.$minor.$patch"
}

final case class PreReleaseBinary(
    major: Int,
    minor: Int,
    patch: Option[Int],
    preRelease: PreRelease
) extends BinaryVersion {
  override def toString: String = {
    val patchPart = patch.map(p => s".$p").getOrElse("")
    s"$major.$minor$patchPart-$preRelease"
  }
}

object BinaryVersion extends Parsers {
  import fastparse.NoWhitespace._
  import fastparse._

  implicit val ordering: Ordering[BinaryVersion] = Ordering.by {
    case MajorBinary(major) =>
      (major, Int.MaxValue, Int.MaxValue, None)
    case MinorBinary(major, minor) =>
      (major, minor, Int.MaxValue, None)
    case PatchBinary(major, minor, patch) =>
      (major, minor, patch, None)
    case PreReleaseBinary(major, minor, patch, preRelease) =>
      (major, minor, patch.getOrElse(Int.MaxValue), Some(preRelease))
  }

  def sortAndFilter(
      binaryVersions: Seq[String],
      filter: BinaryVersion => Boolean
  ): Seq[String] = {
    binaryVersions
      .flatMap(parse)
      .filter(filter)
      .sorted
      .map(_.toString)
  }

  def parse(input: String): Option[BinaryVersion] = {
    tryParse(input, x => FullParser(x))
  }

  def Parser[_: P]: P[BinaryVersion] = {
    MajorParser | MinorParser | PatchParser | PreReleaseParser
  }

  private def FullParser[_: P]: P[BinaryVersion] = Parser ~ End

  private def MajorParser[_: P]: P[MajorBinary] = {
    (Number ~ ".x".? ~ !".").map(MajorBinary)
  }

  private def MinorParser[_: P]: P[MinorBinary] = {
    (Number ~ "." ~ Number ~ !("." | "-")).map { case (major, minor) =>
      MinorBinary(major, minor)
    }
  }

  private def PatchParser[_: P]: P[PatchBinary] = {
    (Number ~ "." ~ Number ~ "." ~ Number ~ !"-").map {
      case (major, minor, patch) =>
        PatchBinary(major, minor, patch)
    }
  }

  private def PreReleaseParser[_: P]: P[PreReleaseBinary] = {
    (Number ~ "." ~ Number ~ ("." ~ Number).? ~ "-" ~ PreRelease.Parser).map {
      case (major, minor, patch, preRelease) =>
        PreReleaseBinary(major, minor, patch, preRelease)
    }
  }
}
