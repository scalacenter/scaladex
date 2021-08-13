package ch.epfl.scala.index.model
package release

import fastparse.NoWhitespace._
import fastparse._

object ScalaTargetType {

  val All: Seq[ScalaTargetType] = Seq(Java, Sbt, Native, Js, Jvm)

  implicit val ordering: Ordering[ScalaTargetType] = Ordering.by(All.indexOf(_))

  def ofName(name: String): Option[ScalaTargetType] =
    All.find(_.getClass.getSimpleName.stripSuffix("$").equalsIgnoreCase(name))
}

sealed trait ScalaTargetType
case object Jvm extends ScalaTargetType

case class PlatformEdition(
    targetType: PlatformVersionedTargetType,
    version: BinaryVersion
) {
  val isValid: Boolean = targetType.isValid(version)
  val render: String = s"${targetType.platformId} $version"
}

case class PlatformAndLanguageVersions(
    language: LanguageVersion,
    platform: BinaryVersion
)

sealed trait PlatformVersionedTargetType extends ScalaTargetType {
  def encode(platformAndLanguageVersions: PlatformAndLanguageVersions): String
  val platformId: String
  val shortName: String
  val platformVersionDeterminesScalaVersion: Boolean = false
  val stableBinaryVersions: Set[BinaryVersion]
  def isValid(version: BinaryVersion): Boolean =
    stableBinaryVersions.contains(version)
  def Parser[_: P]: P[ScalaTarget]
}
case object Js extends PlatformVersionedTargetType {
  val platformId = "scala-js"
  val shortName = "JS"
  def encode(versions: PlatformAndLanguageVersions): String =
    s"_sjs${versions.platform}_${versions.language}"

  val `0.6`: BinaryVersion = MinorBinary(0, 6)
  val `1.x`: BinaryVersion = MajorBinary(1)

  val stableBinaryVersions: Set[BinaryVersion] = Set(`0.6`, `1.x`)

  def Parser[_: P]: P[ScalaTarget] =
    ("_sjs" ~ BinaryVersion.Parser ~ "_" ~ LanguageVersion.Parser).map {
      case (scalaJsVersion, scalaVersion) =>
        ScalaJs(scalaVersion, scalaJsVersion)
    }
}
case object Native extends PlatformVersionedTargetType {
  val platformId = "scala-native"
  val shortName = "Native"
  def encode(versions: PlatformAndLanguageVersions): String =
    s"_native${versions.platform}_${versions.language}"
  val `0.3`: BinaryVersion = MinorBinary(0, 3)
  val `0.4`: BinaryVersion = MinorBinary(0, 4)

  val stableBinaryVersions: Set[BinaryVersion] = Set(`0.3`, `0.4`)

  def Parser[_: P]: P[ScalaTarget] =
    ("_native" ~ BinaryVersion.Parser ~ "_" ~ LanguageVersion.Parser).map {
      case (scalaNativeVersion, scalaVersion) =>
        ScalaNative(scalaVersion, scalaNativeVersion)
    }
}
case object Java extends ScalaTargetType
case object Sbt extends PlatformVersionedTargetType {
  val platformId = "sbt"
  val shortName = "sbt"
  override val platformVersionDeterminesScalaVersion = true
  def encode(versions: PlatformAndLanguageVersions): String =
    s"_${versions.language}_${versions.platform}"

  val `0.13`: BinaryVersion = MinorBinary(0, 13)
  val `1.0`: BinaryVersion = MinorBinary(1, 0)

  val stableBinaryVersions: Set[BinaryVersion] = Set(`0.13`, `1.0`)

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

  def showVersion: String = {
    this match {
      case ScalaJvm(version) => version.toString
      case ScalaJs(version, jsVersion) => s"${jsVersion}_$version"
      case ScalaNative(version, nativeVersion) => s"${nativeVersion}_$version"
      case SbtPlugin(version, sbtVersion) => s"${sbtVersion}_$version"
    }
  }
}

sealed trait ScalaTargetWithPlatformBinaryVersion extends ScalaTarget {
  val platformEdition: PlatformEdition
  lazy val targetType: PlatformVersionedTargetType = platformEdition.targetType

  lazy val isValid: Boolean = languageVersion.isValid && platformEdition.isValid

  lazy val render: String = s"${platformEdition.render} ($languageVersion)"
  lazy val platformAndLanguageVersions: PlatformAndLanguageVersions =
    PlatformAndLanguageVersions(languageVersion, platformEdition.version)
  lazy val encode: String = targetType.encode(platformAndLanguageVersions)
}

case class ScalaJvm(languageVersion: LanguageVersion) extends ScalaTarget {
  val render: String = languageVersion.render
  val encode: String = s"_$languageVersion"
  val targetType: ScalaTargetType = Jvm
  val isValid: Boolean = languageVersion.isValid
}
case class ScalaJs(
    languageVersion: LanguageVersion,
    scalaJsVersion: BinaryVersion
) extends ScalaTargetWithPlatformBinaryVersion {
  val platformEdition: PlatformEdition = PlatformEdition(Js, scalaJsVersion)
}
case class ScalaNative(
    languageVersion: LanguageVersion,
    scalaNativeVersion: BinaryVersion
) extends ScalaTargetWithPlatformBinaryVersion {
  val platformEdition: PlatformEdition =
    PlatformEdition(Native, scalaNativeVersion)
}

case class SbtPlugin(
    languageVersion: LanguageVersion,
    sbtVersion: BinaryVersion
) extends ScalaTargetWithPlatformBinaryVersion {
  val platformEdition: PlatformEdition = PlatformEdition(Sbt, sbtVersion)
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

object ScalaTarget extends Parsers {
  // Scala > Js > Native > Sbt
  implicit val ordering: Ordering[ScalaTarget] =
    Ordering.by[
      ScalaTarget,
      (ScalaTargetType, Option[BinaryVersion], LanguageVersion)
    ] {
      case st: ScalaTargetWithPlatformBinaryVersion =>
        (st.targetType, Some(st.platformEdition.version), st.languageVersion)
      case st: ScalaTarget => (st.targetType, None, st.languageVersion)
    }

  def parse(code: String): Option[ScalaTarget] = {
    tryParse(code, x => FullParser(x))
  }

  def FullParser[_: P]: P[ScalaTarget] =
    Parser ~ End

  def Parser[_: P]: P[ScalaTarget] =
    Js.Parser | Native.Parser | Sbt.Parser | ScalaJvm.Parser
}
