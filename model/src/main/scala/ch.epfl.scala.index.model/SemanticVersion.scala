package ch.epfl.scala.index.model

import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal

/**
 * Semantic version, separation of possible combinations
 *
 * @param major the major version number
 * @param minor the minor version number
 * @param patch the path version number
 * @param patch2 the path version number (to support a.b.c.d)
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

  def isSemantic: Boolean = {
    patch.isDefined &&
    patch2.isEmpty &&
    preRelease.forall(_.isSemantic)
  }

  override def toString: String = {
    val minorPart = minor.map(m => s".$m").getOrElse("")
    val patchPart = patch.map(p => s".$p").getOrElse("")
    val patch2Part = patch2.map(p2 => s".$p2").getOrElse("")
    val preReleasePart = preRelease.map(r => s"-$r").getOrElse("")
    val metadataPart = metadata.map(m => s"+$m").getOrElse("")
    s"$major$minorPart$patchPart$patch2Part$preReleasePart$metadataPart"
  }

  override def compare(that: SemanticVersion): PageIndex = {
    SemanticVersion.ordering.compare(this, that)
  }
}

object SemanticVersion extends Parsers with LazyLogging {
  implicit val ordering: Ordering[SemanticVersion] = Ordering.by { x =>
    (x.major, x.minor, x.patch, x.patch2, x.preRelease)
  }

  def apply(major: Int, minor: Int): SemanticVersion = {
    SemanticVersion(major, Some(minor))
  }

  def apply(major: Int, minor: Int, patch: Int): SemanticVersion = {
    SemanticVersion(major, Some(minor), Some(patch))
  }

  def apply(major: Int, minor: Int, patch: Int, patch2: Int): SemanticVersion = {
    SemanticVersion(major, Some(minor), Some(patch), Some(patch2))
  }

  def apply(major: Int,
            minor: Int,
            patch: Int,
            preRelease: PreRelease): SemanticVersion = {
    SemanticVersion(major, Some(minor), Some(patch), None, Some(preRelease))
  }

  import fastparse._
  import fastparse.NoWhitespace._

  private def Major[_: P] = Number

  // http://semver.org/#spec-item-10
  private def MetaData[_: P] = "+" ~ AnyChar.rep.!

  private def MinorP[_: P] = ("." ~ Number).? // not really valid SemVer
  private def PatchP[_: P] = ("." ~ Number).? // not really valid SemVer
  private def Patch2P[_: P] = ("." ~ Number).? // not really valid SemVer

  def Parser[_: P]: P[SemanticVersion] = {
    ("v".? ~ Major ~ MinorP ~ PatchP ~ Patch2P ~ ("-" ~ PreRelease.Parser).? ~ MetaData.?)
      .map {
        case (major, minor, patch, patch2, preRelease, metadata) =>
          SemanticVersion(major, minor, patch, patch2, preRelease, metadata)
      }
  }

  private def FullParser[_: P] = Start ~ Parser ~ End

  def tryParse(version: String): Option[SemanticVersion] = {
    try {
      fastparse.parse(version, x => FullParser(x)) match {
        case Parsed.Success(v, _) => Some(v)
        case _ =>
          logger.warn(
            s"cannot parse ${classOf[SemanticVersion].getSimpleName} $version"
          )
          None
      }
    } catch {
      case NonFatal(_) =>
        logger.warn(
          s"cannot parse ${classOf[SemanticVersion].getSimpleName} $version"
        )
        None
    }

  }
}
