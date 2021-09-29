package ch.epfl.scala.index.model.release

import ch.epfl.scala.index.model.Parsers
import ch.epfl.scala.index.model.SemanticVersion
import fastparse.NoWhitespace._
import fastparse._

sealed trait Platform extends Ordered[Platform] with Product with Serializable {
  def scalaVersion: Option[ScalaLanguageVersion]
  def platformVersion: Option[BinaryVersion]
  def render: String
  def encode: String
  def shortName: String
  def platformType: Platform.Type
  def isValid: Boolean
  def showVersion: String =
    this match {
      case Platform.ScalaJvm(version) => version.toString
      case Platform.ScalaJs(version, jsVersion) => s"${jsVersion}_$version"
      case Platform.ScalaNative(version, nativeVersion) =>
        s"${nativeVersion}_$version"
      case Platform.SbtPlugin(version, sbtVersion) => s"${sbtVersion}_$version"
      case Platform.Java => "Java"
    }
  override def compare(that: Platform): Int =
    Platform.ordering.compare(this, that)

  def isJava: Boolean = this match {
    case Platform.Java => true
    case _ => false
  }
  def isSbt: Boolean = this match {
    case _: Platform.SbtPlugin => true
    case _ => false
  }
}
object Platform extends Parsers {
  sealed trait Type extends Product with Serializable

  object Type {
    val All: Seq[Type] = Seq(Java, Sbt, Native, Js, Jvm)

    implicit val ordering: Ordering[Type] = Ordering.by(All.indexOf(_))

    def ofName(name: String): Option[Type] =
      All.find(_.getClass.getSimpleName.stripSuffix("$").equalsIgnoreCase(name))

    case object Java extends Type
    case object Sbt extends Type
    case object Native extends Type
    case object Js extends Type
    case object Jvm extends Type
  }

  // Scala > Js > Native > Sbt > Java
  implicit val ordering: Ordering[Platform] =
    Ordering.by[
      Platform,
      (Platform.Type, Option[BinaryVersion], Option[ScalaLanguageVersion])
    ] { platform =>
      (platform.platformType, platform.platformVersion, platform.scalaVersion)
    }

  case class SbtPlugin(scalaV: ScalaLanguageVersion, sbtV: BinaryVersion)
      extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = Some(scalaV)
    override def platformVersion: Option[BinaryVersion] = Some(sbtV)
    override def platformType: Platform.Type = Type.Sbt
    override def render: String = s"sbt $platformVersion ($scalaV)"
    override def encode: String = s"_${scalaV}_${sbtV}"
    override def shortName: String = "sbt"
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
    override def shortName: String = "java"
    override def platformType: Platform.Type = Type.Java
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
    override def shortName: String = "Native"
    override def platformType: Platform.Type = Type.Native
    override def isValid: Boolean =
      scalaV.isValid && ScalaNative.isValid(scalaNativeV)
  }

  object ScalaNative {
    val `0.3`: BinaryVersion = MinorBinary(0, 3)
    val `0.4`: BinaryVersion = MinorBinary(0, 4)
    private val stableBinaryVersions: Set[BinaryVersion] = Set(`0.3`, `0.4`)
    def isValid(version: BinaryVersion): Boolean =
      stableBinaryVersions.contains(version)
  }

  case class ScalaJs(scalaV: ScalaLanguageVersion, scalaJsV: BinaryVersion)
      extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = Some(scalaV)

    override def platformVersion: Option[BinaryVersion] = Some(scalaJsV)
    override def render: String = s"scala-js $scalaJsV ($scalaV)"
    override def encode: String = s"_sjs${scalaJsV}_$scalaV"
    override def shortName: String = "JS"
    override def platformType: Platform.Type = Type.Js
    override def isValid: Boolean =
      scalaV.isValid && ScalaJs.isValid(scalaJsV)
  }

  object ScalaJs {
    val `0.6`: BinaryVersion = MinorBinary(0, 6)
    val `1.x`: BinaryVersion = MajorBinary(1)

    private val stableBinaryVersions: Set[BinaryVersion] = Set(`0.6`, `1.x`)
    def isValid(version: BinaryVersion): Boolean =
      stableBinaryVersions.contains(version)
  }

  case class ScalaJvm(scalaV: ScalaLanguageVersion) extends Platform {
    override def scalaVersion: Option[ScalaLanguageVersion] = Some(scalaV)
    override def platformVersion: Option[BinaryVersion] = None
    override def render: String = "scalaV.render"
    override def platformType: Platform.Type = Type.Jvm
    override def encode: String = s"_$scalaV"
    override def shortName: String = "Scala"
    override def isValid: Boolean = scalaVersion.exists(_.isValid)
  }

  object ScalaJvm {
    def fromFullVersion(fullVersion: SemanticVersion): Option[ScalaJvm] = {
      val binaryVersion = fullVersion match {
        case SemanticVersion(major, Some(minor), _, None, None, None)
            if major == 2 =>
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

  def IntermediateParser[_: P]
      : P[(String, Option[BinaryVersion], Option[BinaryVersion])] = {
    ("_sjs" | "_native" | "_" | "").! ~ (BinaryVersion.Parser.?) ~ ("_" ~ BinaryVersion.Parser).?
  }
  def Parser[_: P]: P[Platform] = {
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

}
