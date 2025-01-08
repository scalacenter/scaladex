package scaladex.core.model

import scaladex.core.util.Parsers

import fastparse.*
import fastparse.NoWhitespace.*

final case class BinaryVersion(platform: Platform, language: Language):
  def isValid: Boolean = platform.isValid && language.isValid

  // possibly empty
  def asSuffix: String = (platform, language) match
    case (Jvm, Java) => ""
    case _ => value

  // non-empty
  def value: String = (platform, language) match
    case (Jvm, Java) => language.value
    case (Jvm, Scala(sv)) => s"_${sv.value}"
    case (SbtPlugin(sbtV), Scala(sv)) =>
      sbtV match
        case Version.Major(1) => s"_${sv.value}_1.0"
        case Version.Minor(0, 13) => s"_${sv.value}_0.13"
        case sbtV => s"_sbt${sbtV.value}_${sv.value}"
    case (platform, language) => s"_${platform.value}_${language.value}"

  override def toString: String = platform match
    case Jvm => language.toString
    case p: SbtPlugin => p.toString
    case p: MillPlugin => p.toString
    case _ => s"$platform ($language)"
end BinaryVersion

object BinaryVersion:
  given ordering: Ordering[BinaryVersion] = Ordering.by(v => (v.platform, v.language))

  def IntermediateParser[A: P]: P[(String, Option[Version], Option[Version])] =
    ("_sjs" | "_native" | "_mill" | "_sbt" | "_" | "").! ~ (Version.SemanticParser.?) ~ ("_" ~ Version.SemanticParser).?

  def IntermediateParserButNotInvalidSbt[A: P]: P[(String, Option[Version], Option[Version])] =
    IntermediateParser.filter {
      case ("_", Some(scalaV), Some(sbtV)) => BinaryVersion(SbtPlugin(sbtV), Scala(scalaV)).isValid
      case _ => true
    }

  def Parser[A: P]: P[BinaryVersion] =
    IntermediateParser
      .map {
        case ("_sjs", Some(jsV), Some(scalaV)) => Some(BinaryVersion(ScalaJs(jsV), Scala(scalaV)))
        case ("_native", Some(nativeV), Some(scalaV)) => Some(BinaryVersion(ScalaNative(nativeV), Scala(scalaV)))
        case ("_mill", Some(millV), Some(scalaV)) => Some(BinaryVersion(MillPlugin(millV), Scala(scalaV)))
        case ("_sbt", Some(sbtV), Some(scalaV)) => Some(BinaryVersion(SbtPlugin(sbtV), Scala(scalaV)))
        case ("_", Some(scalaV), Some(sbtV)) => Some(BinaryVersion(SbtPlugin(sbtV), Scala(scalaV)))
        case ("_", Some(scalaV), None) => Some(BinaryVersion(Jvm, Scala(scalaV)))
        case ("", None, None) => Some(BinaryVersion(Jvm, Java))
        case _ => None
      }
      .filter(_.isDefined)
      .map(_.get)
      .filter(_.isValid)

  def FullParser[A: P]: P[BinaryVersion] =
    Parser ~ End

  def parse(input: String): Option[BinaryVersion] =
    input match
      case "java" => Some(BinaryVersion(Jvm, Java))
      case _ => Parsers.tryParse(input, x => FullParser(x))
end BinaryVersion
