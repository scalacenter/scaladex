package ch.epfl.scala.index.model
package release

object ScalaTargetType {
  implicit val ordering: Ordering[ScalaTargetType] = Ordering.by {
    case Jvm    => 5
    case Js     => 4
    case Native => 3
    case Sbt    => 2
    case Java   => 1
  }
}

sealed trait ScalaTargetType
case object Jvm extends ScalaTargetType
case object Js extends ScalaTargetType
case object Native extends ScalaTargetType
case object Java extends ScalaTargetType
case object Sbt extends ScalaTargetType

/*
SemanticVersion will only contain the information from the artifactId

if a library is not cross-published with full, then version.full == version.binary
in other words if we see cats_2.11 we will not infer the full scala version from
it's dependency on scala-library for example.

 */
sealed trait ScalaTarget extends Ordered[ScalaTarget] {
  def scalaVersion: BinaryVersion
  def render: String
  def encode: String
  def targetType: ScalaTargetType
  def isValid: Boolean

  override def compare(that: ScalaTarget): Int =
    ScalaTarget.ordering.compare(this, that)
}

case class ScalaJvm(scalaVersion: BinaryVersion) extends ScalaTarget {
  val render = s"scala $scalaVersion"
  val encode = s"_$scalaVersion"
  val targetType: ScalaTargetType = Jvm
  val isValid: Boolean = ScalaJvm.isValid(scalaVersion)
}
case class ScalaJs(scalaVersion: BinaryVersion, scalaJsVersion: BinaryVersion)
    extends ScalaTarget {
  val render = s"scala-js $scalaJsVersion ($scalaVersion)"
  val encode = s"_sjs${scalaJsVersion}_$scalaVersion"
  val targetType: ScalaTargetType = Js
  val isValid: Boolean = ScalaJvm.isValid(scalaVersion) && ScalaJs.isValid(
    scalaJsVersion
  )
}
case class ScalaNative(scalaVersion: BinaryVersion,
                       scalaNativeVersion: BinaryVersion)
    extends ScalaTarget {
  val render = s"scala-native $scalaNativeVersion ($scalaVersion)"
  val encode = s"_native${scalaNativeVersion}_$scalaVersion"
  val targetType: ScalaTargetType = Native
  val isValid: Boolean = ScalaJvm.isValid(scalaVersion) && ScalaNative.isValid(
    scalaNativeVersion
  )
}

case class SbtPlugin(scalaVersion: BinaryVersion, sbtVersion: BinaryVersion)
    extends ScalaTarget {
  val render = s"sbt $sbtVersion ($scalaVersion)"
  val encode = s"_${scalaVersion}_$sbtVersion"
  val targetType: ScalaTargetType = Sbt
  val isValid: Boolean = ScalaJvm.isValid(scalaVersion) && SbtPlugin.isValid(
    sbtVersion
  )
}

object ScalaJvm {
  def fromFullVersion(fullVersion: SemanticVersion): ScalaJvm = {
    val binaryVersion = fullVersion match {
      case SemanticVersion(major, minor, patch, _, Some(preRelease), _) =>
        PreReleaseBinary(
          major,
          minor.getOrElse(0),
          patch,
          preRelease
        )
      case SemanticVersion(major, minor, _, _, _, _) =>
        MinorBinary(major, minor.getOrElse(0))
    }
    ScalaJvm(binaryVersion)
  }

  val `2.10`: BinaryVersion = MinorBinary(2, 10)
  val `2.11`: BinaryVersion = MinorBinary(2, 11)
  val `2.12`: BinaryVersion = MinorBinary(2, 12)
  val `2.13`: BinaryVersion = MinorBinary(2, 13)

  private def stableBinaryVersions = Set(`2.10`, `2.11`, `2.12`, `2.13`)

  def isValid(version: BinaryVersion): Boolean =
    stableBinaryVersions.contains(version)
}

object ScalaJs {
  val `0.6`: BinaryVersion = MinorBinary(0, 6)
  val `1.x`: BinaryVersion = MajorBinary(1)

  private def stableBinaryVersions = Set(`0.6`, `1.x`)

  def isValid(version: BinaryVersion): Boolean =
    stableBinaryVersions.contains(version)
}

object ScalaNative {
  val `0.3`: BinaryVersion = MinorBinary(0, 3)
  val `0.4.0-M2`: BinaryVersion = PreReleaseBinary(0, 4, Some(0), Milestone(2))

  def isValid(version: BinaryVersion): Boolean = {
    version == `0.3` || version >= `0.4.0-M2`
  }
}

object SbtPlugin {
  val `0.13`: BinaryVersion = MinorBinary(0, 13)
  val `1.0`: BinaryVersion = MinorBinary(1, 0)
  val `1.x`: BinaryVersion = MajorBinary(1)

  private def stableBinaryVersions = Set(`0.13`, `1.0`, `1.x`)

  def isValid(version: BinaryVersion): Boolean = {
   stableBinaryVersions.contains(version)
  }
}

object ScalaTarget extends Parsers {
  // Scala > Js > Native > Sbt
  implicit val ordering: Ordering[ScalaTarget] =
    Ordering.by[
      ScalaTarget,
      (ScalaTargetType, BinaryVersion, Option[BinaryVersion])
    ] {
      case ScalaJvm(version)           => (Jvm, version, None)
      case ScalaJs(version, jsVersion) => (Js, jsVersion, Some(version))
      case ScalaNative(version, nativeVersion) => (Native, nativeVersion, Some(version))
      case SbtPlugin(version, sbtVersion) => (Sbt, sbtVersion, Some(version))
    }

  def parse(code: String): Option[ScalaTarget] = {
    tryParse(code, x => Parser(x))
  }

  import fastparse.NoWhitespace._
  import fastparse._

  private def scalaPart[_: P] = "_" ~ BinaryVersion.Parser

  private def scalaJs[_: P]: P[ScalaTarget] =
    ("_sjs" ~ BinaryVersion.Parser ~ scalaPart).map {
      case (scalaJsVersion, scalaVersion) =>
        ScalaJs(scalaVersion, scalaJsVersion)
    }

  private def scalaNative[_: P]: P[ScalaTarget] =
    ("_native" ~ BinaryVersion.Parser ~ scalaPart).map {
      case (scalaNativeVersion, scalaVersion) =>
        ScalaNative(scalaVersion, scalaNativeVersion)
    }

  private def sbt[_: P]: P[ScalaTarget] =
    (scalaPart ~ "_" ~ BinaryVersion.Parser).map {
      case (scalaVersion, sbtVersion) =>
        SbtPlugin(scalaVersion, sbtVersion)
    }

  private def scala[_: P]: P[ScalaTarget] = scalaPart.map(ScalaJvm.apply)

  def Parser[_: P]: P[ScalaTarget] =
    scalaJs | scalaNative | sbt | scala
}
