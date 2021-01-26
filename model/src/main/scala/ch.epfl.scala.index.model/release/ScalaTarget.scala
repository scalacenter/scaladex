package ch.epfl.scala.index.model
package release

import ch.epfl.scala.index.model.release
import fastparse.NoWhitespace._
import fastparse._

object ScalaTargetType {
  implicit val ordering: Ordering[ScalaTargetType] = Ordering.by {
    case Jvm => 5
    case Js => 4
    case Native => 3
    case Sbt => 2
    case Java => 1
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
  def languageVersion: LanguageVersion
  def render: String
  def encode: String
  def targetType: ScalaTargetType
  def isValid: Boolean

  override def compare(that: ScalaTarget): Int =
    ScalaTarget.ordering.compare(this, that)
}

case class ScalaJvm(languageVersion: LanguageVersion) extends ScalaTarget {
  val render = languageVersion.render
  val encode = s"_$languageVersion"
  val targetType: ScalaTargetType = Jvm
  val isValid: Boolean = languageVersion.isValid
}
case class ScalaJs(
    languageVersion: LanguageVersion,
    scalaJsVersion: BinaryVersion
) extends ScalaTarget {
  val render = s"scala-js $scalaJsVersion (${languageVersion.render})"
  val encode = s"_sjs${scalaJsVersion}_$languageVersion"
  val targetType: ScalaTargetType = Js
  val isValid: Boolean = languageVersion.isValid && ScalaJs.isValid(
    scalaJsVersion
  )
}
case class ScalaNative(
    languageVersion: LanguageVersion,
    scalaNativeVersion: BinaryVersion
) extends ScalaTarget {
  val render = s"scala-native $scalaNativeVersion (${languageVersion.render})"
  val encode = s"_native${scalaNativeVersion}_$languageVersion"
  val targetType: ScalaTargetType = Native
  val isValid: Boolean = languageVersion.isValid && ScalaNative.isValid(
    scalaNativeVersion
  )
}

case class SbtPlugin(
    languageVersion: LanguageVersion,
    sbtVersion: BinaryVersion
) extends ScalaTarget {
  val render = s"sbt $sbtVersion ($languageVersion)"
  val encode = s"_${languageVersion}_$sbtVersion"
  val targetType: ScalaTargetType = Sbt
  val isValid: Boolean = languageVersion.isValid && SbtPlugin.isValid(
    sbtVersion
  )
}

object ScalaJvm {
  def fromFullVersion(fullVersion: SemanticVersion): Option[ScalaJvm] = {
    val binaryVersion = fullVersion match {
      case SemanticVersion(major, Some(minor), _, None, None, None) =>
        Some(MinorBinary(major, minor))
      case _ => None
    }

    binaryVersion
      .collect {
        case version if ScalaVersion.isValid(version) => ScalaVersion(version)
        case version if Scala3Version.isValid(version) => Scala3Version(version)
      }
      .map(ScalaJvm(_))
  }

  def Parser[_: P]: P[ScalaTarget] =
    ("_" ~ LanguageVersion.Parser).map(ScalaJvm.apply)
}

object ScalaJs {
  val `0.6`: BinaryVersion = MinorBinary(0, 6)
  val `1.x`: BinaryVersion = MajorBinary(1)

  private val stableBinaryVersions = Set(`0.6`, `1.x`)

  def isValid(version: BinaryVersion): Boolean =
    stableBinaryVersions.contains(version)

  def Parser[_: P]: P[ScalaTarget] =
    ("_sjs" ~ BinaryVersion.Parser ~ "_" ~ LanguageVersion.Parser).map {
      case (scalaJsVersion, scalaVersion) =>
        ScalaJs(scalaVersion, scalaJsVersion)
    }
}

object ScalaNative {
  val `0.3`: BinaryVersion = MinorBinary(0, 3)
  val `0.4`: BinaryVersion = MinorBinary(0, 4)

  private val stableBinaryVersions = Set(`0.3`, `0.4`)

  def isValid(version: BinaryVersion): Boolean =
    stableBinaryVersions.contains(version)

  def Parser[_: P]: P[ScalaTarget] =
    ("_native" ~ BinaryVersion.Parser ~ "_" ~ LanguageVersion.Parser).map {
      case (scalaNativeVersion, scalaVersion) =>
        ScalaNative(scalaVersion, scalaNativeVersion)
    }
}

object SbtPlugin {
  val `0.13`: BinaryVersion = MinorBinary(0, 13)
  val `1.0`: BinaryVersion = MinorBinary(1, 0)

  private val stableBinaryVersions = Set(`0.13`, `1.0`)

  def isValid(version: BinaryVersion): Boolean = {
    stableBinaryVersions.contains(version)
  }

  def Parser[_: P]: P[ScalaTarget] =
    ("_" ~ LanguageVersion.Parser ~ "_" ~ BinaryVersion.Parser.filter(isValid))
      .map { case (scalaVersion, sbtVersion) =>
        SbtPlugin(scalaVersion, sbtVersion)
      } | ("_" ~ BinaryVersion.Parser.filter(
      isValid
    ) ~ "_" ~ LanguageVersion.Parser)
      .map { case (scalaVersion, sbtVersion) =>
        SbtPlugin(sbtVersion, scalaVersion)
      }
}

object ScalaTarget extends Parsers {
  // Scala > Js > Native > Sbt
  implicit val ordering: Ordering[ScalaTarget] =
    Ordering.by[
      ScalaTarget,
      (ScalaTargetType, Option[BinaryVersion], LanguageVersion)
    ] {
      case ScalaJvm(version) => (Jvm, None, version)
      case ScalaJs(version, jsVersion) => (Js, Some(jsVersion), version)
      case ScalaNative(version, nativeVersion) =>
        (Native, Some(nativeVersion), version)
      case SbtPlugin(version, sbtVersion) => (Sbt, Some(sbtVersion), version)
    }

  def parse(code: String): Option[ScalaTarget] = {
    tryParse(code, x => FullParser(x))
  }

  def FullParser[_: P]: P[ScalaTarget] =
    Parser ~ End

  def Parser[_: P]: P[ScalaTarget] =
    ScalaJs.Parser | ScalaNative.Parser | SbtPlugin.Parser | ScalaJvm.Parser
}
