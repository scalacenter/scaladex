package scaladex.core.model

import fastparse.NoWhitespace._
import fastparse._
import scaladex.core.util.Parsers

final case class BinaryVersion(platform: Platform, language: Language) {
  def isValid: Boolean = platform.isValid && language.isValid
  def encode: String = (platform, language) match {
    case (Jvm, Java)                  => ""
    case (Jvm, Scala(sv))             => s"_${sv.encode}"
    case (SbtPlugin(sbtV), Scala(sv)) => s"_${sv.encode}_${sbtV.encode}"
    case (platform, Scala(sv))        => s"_${platform.label}_${sv.encode}"
    case (platform, Java)             => s"_${platform.label}"
  }
  def label: String = (platform, language) match {
    case (Jvm, Java) => "Java"
    case _           => encode
  }
  override def toString: String = (platform, language) match {
    case (Jvm, Java)           => "Java"
    case (Jvm, Scala(version)) => s"Scala $version"
    case (_, _)                => s"$platform ($language)"
  }
}

object BinaryVersion {
  implicit val ordering: Ordering[BinaryVersion] = Ordering.by(v => (v.platform, v.language))

  def IntermediateParser[A: P]: P[(String, Option[SemanticVersion], Option[SemanticVersion])] =
    ("_sjs" | "_native" | "_mill" | "_" | "").! ~ (SemanticVersion.Parser.?) ~ ("_" ~ SemanticVersion.Parser).?

  def IntermediateParserButNotInvalidSbt[A: P]: P[(String, Option[SemanticVersion], Option[SemanticVersion])] =
    IntermediateParser.filter {
      case ("_", Some(scalaV), Some(sbtV)) => BinaryVersion(SbtPlugin(sbtV), Scala(scalaV)).isValid
      case _                               => true
    }

  def Parser[A: P]: P[BinaryVersion] =
    IntermediateParser
      .map {
        case ("_sjs", Some(jsV), Some(scalaV))        => Some(BinaryVersion(ScalaJs(jsV), Scala(scalaV)))
        case ("_native", Some(nativeV), Some(scalaV)) => Some(BinaryVersion(ScalaNative(nativeV), Scala(scalaV)))
        case ("_mill", Some(millV), Some(scalaV))     => Some(BinaryVersion(MillPlugin(millV), Scala(scalaV)))
        case ("_", Some(scalaV), Some(sbtV))          => Some(BinaryVersion(SbtPlugin(sbtV), Scala(scalaV)))
        case ("_", Some(scalaV), None)                => Some(BinaryVersion(Jvm, Scala(scalaV)))
        case ("", None, None)                         => Some(BinaryVersion(Jvm, Java))
        case _                                        => None
      }
      .filter(_.isDefined)
      .map(_.get)
      .filter(_.isValid)

  def FullParser[A: P]: P[BinaryVersion] =
    Parser ~ End

  def parse(input: String): Option[BinaryVersion] = Parsers.tryParse(input, x => FullParser(x))

  def fromLabel(label: String): Option[BinaryVersion] = label match {
    case "Java" => Some(BinaryVersion(Jvm, Java))
    case _      => parse(label)
  }
}
