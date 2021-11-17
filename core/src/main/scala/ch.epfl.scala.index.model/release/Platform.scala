package ch.epfl.scala.index.model.release

import ch.epfl.scala.index.model.Parsers
import ch.epfl.scala.index.model.SemanticVersion
import fastparse.NoWhitespace._
import fastparse._

sealed trait Platform extends Ordered[Platform] with Product with Serializable {
  import ch.epfl.scala.index.model.release.Platform._

  def scalaVersion: Option[ScalaLanguageVersion]
  def platformVersion: Option[BinaryVersion]
  def render: String
  def encode: String
  def platformType: PlatformType
  def isValid: Boolean
  def showVersion: String =
    this match {
      case ScalaJvm(version)           => version.toString
      case ScalaJs(version, jsVersion) => s"${jsVersion}_$version"
      case ScalaNative(version, nativeVersion) =>
        s"${nativeVersion}_$version"
      case SbtPlugin(version, sbtVersion) => s"${sbtVersion}_$version"
      case Java                           => "Java"
    }
  override def compare(that: Platform): Int =
    ordering.compare(this, that)

  def isJava: Boolean = this match {
    case Java => true
    case _    => false
  }
  def isSbt: Boolean = this match {
    case _: SbtPlugin => true
    case _            => false
  }
}

object Platform extends Parsers {
  sealed trait PlatformType extends Product with Serializable {
    def name: String = toString.toLowerCase
    def label: String
  }

  object PlatformType {
    val all: Seq[PlatformType] = Seq(Java, Sbt, Native, Js, Jvm)

    implicit val ordering: Ordering[PlatformType] = Ordering.by(all.indexOf(_))

    def ofName(name: String): Option[PlatformType] =
      all.find(_.name == name.toLowerCase)

    case object Java extends PlatformType {
      def label: String = "Java"
    }
    case object Sbt extends PlatformType {
      def label: String = "sbt"
    }
    case object Native extends PlatformType {
      def label: String = "Scala Native"
    }
    case object Js extends PlatformType {
      def label: String = "Scala.js"
    }
    case object Jvm extends PlatformType {
      def label: String = "Scala (JVM)"
    }
  }

  // Scala > Js > Native > Sbt > Java
  implicit val ordering: Ordering[Platform] =
    Ordering.by[
      Platform,
      (PlatformType, Option[BinaryVersion], Option[ScalaLanguageVersion])
    ](platform => (platform.platformType, platform.platformVersion, platform.scalaVersion))

  case class SbtPlugin(scalaV: ScalaLanguageVersion, sbtV: BinaryVersion) extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = Some(scalaV)
    override def platformVersion: Option[BinaryVersion] = Some(sbtV)
    override def platformType: PlatformType = PlatformType.Sbt
    override def render: String = s"sbt $sbtV ($scalaV)"
    override def encode: String = s"_${scalaV}_${sbtV}"
    override def isValid: Boolean =
      scalaV.isValid && SbtPlugin.isValid(sbtV)
  }

  object SbtPlugin {
    val `0.13`: BinaryVersion = MinorBinary(0, 13)
    val `1.0`: BinaryVersion = MinorBinary(1, 0)
    val stableBinaryVersions: Set[BinaryVersion] = Set(`0.13`, `1.0`)
    def isValid(version: BinaryVersion): Boolean =
      stableBinaryVersions.contains(version)
  }

  case object Java extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = None
    override def platformVersion: Option[BinaryVersion] = None
    override def render: String = "Java"
    override def encode: String = ""
    override def platformType: PlatformType = PlatformType.Java
    override def isValid: Boolean = true
  }

  case class ScalaNative(
      scalaV: ScalaLanguageVersion,
      scalaNativeV: BinaryVersion
  ) extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = Some(scalaV)
    override def platformVersion: Option[BinaryVersion] = Some(scalaNativeV)
    override def render: String = s"scala-native $scalaNativeV ($scalaV)"
    override def encode: String = s"_native${scalaNativeV}_${scalaV}"
    override def platformType: PlatformType = PlatformType.Native
    override def isValid: Boolean =
      scalaV.isValid && ScalaNative.isValid(scalaNativeV)
  }

  object ScalaNative {
    val `0.3`: BinaryVersion = MinorBinary(0, 3)
    val `0.4`: BinaryVersion = MinorBinary(0, 4)
    val `0.4_2.13`: ScalaNative = ScalaNative(ScalaVersion.`2.13`, `0.4`)
    private val stableBinaryVersions: Set[BinaryVersion] = Set(`0.3`, `0.4`)
    def isValid(version: BinaryVersion): Boolean =
      stableBinaryVersions.contains(version)
  }

  case class ScalaJs(scalaV: ScalaLanguageVersion, scalaJsV: BinaryVersion) extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = Some(scalaV)

    override def platformVersion: Option[BinaryVersion] = Some(scalaJsV)
    override def render: String = s"scala-js $scalaJsV ($scalaV)"
    override def encode: String = s"_sjs${scalaJsV}_$scalaV"
    override def platformType: PlatformType = PlatformType.Js
    override def isValid: Boolean =
      scalaV.isValid && ScalaJs.isValid(scalaJsV)
  }

  object ScalaJs {
    val `0.6`: BinaryVersion = MinorBinary(0, 6)
    val `1.x`: BinaryVersion = MajorBinary(1)
    val `0.6_2.13`: ScalaJs = ScalaJs(ScalaVersion.`2.13`, `0.6`)
    val `1_3`: ScalaJs = ScalaJs(Scala3Version.`3`, `1.x`)

    private val stableBinaryVersions: Set[BinaryVersion] = Set(`0.6`, `1.x`)
    def isValid(version: BinaryVersion): Boolean =
      stableBinaryVersions.contains(version)
  }

  case class ScalaJvm(scalaV: ScalaLanguageVersion) extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = Some(scalaV)
    override def platformVersion: Option[BinaryVersion] = None
    override def render: String = scalaV.render
    override def platformType: PlatformType = PlatformType.Jvm
    override def encode: String = s"_${scalaV}"
    override def isValid: Boolean = scalaVersion.exists(_.isValid)
  }

  object ScalaJvm {
    val `3`: ScalaJvm = ScalaJvm(Scala3Version.`3`)
    val `2.13` = ScalaJvm(ScalaVersion.`2.13`)

    def fromFullVersion(fullVersion: SemanticVersion): Option[ScalaJvm] = {
      val binaryVersion = fullVersion match {
        case SemanticVersion(major, Some(minor), _, None, None, None) if major == 2 =>
          Some(MinorBinary(major, minor))
        case SemanticVersion(major, _, _, None, None, None) if major == 3 =>
          Some(MajorBinary(major))
        case _ => None
      }

      binaryVersion
        .collect {
          case version if ScalaVersion.isValid(version) => ScalaVersion(version)
          case version if Scala3Version.isValid(version) =>
            Scala3Version(version)
        }
        .map(ScalaJvm(_))
    }
  }

  def parse(code: String): Option[Platform] =
    tryParse(code, x => FullParser(x))

  def FullParser[_: P]: P[Platform] =
    Parser ~ End

  def IntermediateParser[_: P]: P[(String, Option[BinaryVersion], Option[BinaryVersion])] =
    ("_sjs" | "_native" | "_" | "").! ~ (BinaryVersion.Parser.?) ~ ("_" ~ BinaryVersion.Parser).?
  def Parser[_: P]: P[Platform] =
    IntermediateParser
      .map {
        case ("_sjs", Some(jsV), Some(scalaV)) =>
          ScalaLanguageVersion.from(scalaV).map(sv => ScalaJs(sv, jsV))
        case ("_native", Some(nativeV), Some(scalaV)) =>
          ScalaLanguageVersion.from(scalaV).map(sv => ScalaNative(sv, nativeV))
        case ("_", Some(scalaV), Some(sbtV)) =>
          ScalaLanguageVersion.from(scalaV).map(sv => SbtPlugin(sv, sbtV))
        case ("_", Some(scalaV), None) =>
          ScalaLanguageVersion.from(scalaV).map(sv => ScalaJvm(sv))
        case ("", None, None) =>
          Some(Java)
        case _ => None
      }
      .filter(_.isDefined)
      .map(_.get)
      .filter(_.isValid)

}
