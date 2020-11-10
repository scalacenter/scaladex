package ch.epfl.scala.index.model
package release

import fastparse.NoWhitespace._
import fastparse._

/**
 * A [[LanguageVersion]] is the binary version of the compiler frontend.
 * It can be either a [[ScalaVersion]] or a [[Scala3Version]]
 */
sealed trait LanguageVersion {

  /**
   * When indexing, all dotty versions are regrouped under the 'dotty' keyword.
   * That keyword is called the family of the version.
   * @return family
   */
  def family: String
  def render: String

  /**
   * This method is used to discard deprecated or ill-formated versions.
   * It returns true if the version is relevant and false otherwise.
   * @return isValid
   */
  def isValid: Boolean
}

final case class ScalaVersion(version: BinaryVersion) extends LanguageVersion {
  def family: String = toString()
  def render: String = s"scala $version"
  def isValid: Boolean = ScalaVersion.isValid(version)
  override def toString: String = version.toString()
}

final case class Scala3Version(version: BinaryVersion) extends LanguageVersion {
  def family = "scala3"
  def render: String = version match {
    case MinorBinary(major, minor) if major == 0 && minor < 30 =>
      s"dotty $version"
    case PreReleaseBinary(major, _, _, _) if major == 3 => s"scala $toString"
    case _ => toString()
  }
  def isValid: Boolean = Scala3Version.isValid(version)
  override def toString: String = version.toString()
}

object ScalaVersion {
  val `2.10`: ScalaVersion = ScalaVersion(MinorBinary(2, 10))
  val `2.11`: ScalaVersion = ScalaVersion(MinorBinary(2, 11))
  val `2.12`: ScalaVersion = ScalaVersion(MinorBinary(2, 12))
  val `2.13`: ScalaVersion = ScalaVersion(MinorBinary(2, 13))

  private val stableBinaryVersions =
    Set(`2.10`.version, `2.11`.version, `2.12`.version, `2.13`.version)

  def isValid(version: BinaryVersion): Boolean =
    stableBinaryVersions.contains(version)

  def Parser[_: P]: P[ScalaVersion] = {
    BinaryVersion.Parser
      .filter(isValid)
      .map(ScalaVersion(_))
  }
}

object Scala3Version {
  def isValid(version: BinaryVersion): Boolean =
    version match {
      case MinorBinary(major, minor) if major == 0 && minor < 30 =>
        true // dotty versions
      case PreReleaseBinary(major, _, _, _) if major == 3 => true
      case _ => false
    }

  def Parser[_: P]: P[Scala3Version] = {
    BinaryVersion.Parser
      .filter(isValid)
      .map(Scala3Version(_))
  }
}

object LanguageVersion extends Parsers {
  def tryParse(version: String): Option[LanguageVersion] = {
    tryParse(version, x => FullParser(x))
  }

  def sortFamilies(languageFamilies: List[String]): List[String] = {
    languageFamilies.sorted // alphabetical order: 2.10 < 2.11 < 2.12 < 2.13 < dotty
  }

  implicit val ordering: Ordering[LanguageVersion] = Ordering.by {
    case Scala3Version(version) => (1, version)
    case ScalaVersion(version) => (0, version)
  }

  def Parser[_: P]: P[LanguageVersion] =
    ScalaVersion.Parser | Scala3Version.Parser

  def FullParser[_: P]: P[LanguageVersion] = Parser ~ End
}
