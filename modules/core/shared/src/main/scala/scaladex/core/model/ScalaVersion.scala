package scaladex.core.model

import fastparse.NoWhitespace._
import fastparse._
import scaladex.core.util.Parsers

final case class ScalaVersion(version: BinaryVersion) {
  def encode: String = version.encode
  def isValid: Boolean = ScalaVersion.isValid(version)
  def render: String = version match {
    case MajorBinary(x) => s"Scala $x"
    case v              => s"Scala $v"
  }
  override def toString: String = version.toString
}

object ScalaVersion {
  val `2.10`: ScalaVersion = ScalaVersion(MinorBinary(2, 10))
  val `2.11`: ScalaVersion = ScalaVersion(MinorBinary(2, 11))
  val `2.12`: ScalaVersion = ScalaVersion(MinorBinary(2, 12))
  val `2.13`: ScalaVersion = ScalaVersion(MinorBinary(2, 13))
  val `3`: ScalaVersion = ScalaVersion(MajorBinary(3))

  private val stableBinaryVersions =
    Set(`2.10`.version, `2.11`.version, `2.12`.version, `2.13`.version, `3`.version)

  def isValid(version: BinaryVersion): Boolean =
    stableBinaryVersions.contains(version)

  def Parser[_: P]: P[ScalaVersion] =
    BinaryVersion.Parser.filter(isValid).map(ScalaVersion(_))

  def FullParser[_: P]: P[ScalaVersion] = Parser ~ End

  def parse(version: String): Option[ScalaVersion] =
    Parsers.tryParse(version, x => FullParser(x))

  def from(binaryV: BinaryVersion): Option[ScalaVersion] =
    Option.when(isValid(binaryV))(ScalaVersion(binaryV))

  implicit val ordering: Ordering[ScalaVersion] = Ordering.by(_.version)
}
