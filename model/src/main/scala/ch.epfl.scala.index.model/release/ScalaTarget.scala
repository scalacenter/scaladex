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
  def scalaVersion: SemanticVersion
  def render: String
  def encode: String
  def targetType: ScalaTargetType

  override def compare(that: ScalaTarget): Int =
    ScalaTarget.ordering.compare(this, that)
}

case class ScalaJvm(scalaVersion: SemanticVersion) extends ScalaTarget {
  val scalaJsVersion: Option[SemanticVersion] = None
  val render = s"scala $scalaVersion"
  val encode = s"_$scalaVersion"
  val targetType: ScalaTargetType = Jvm
}
case class ScalaJs(scalaVersion: SemanticVersion,
                   scalaJsVersion: SemanticVersion)
    extends ScalaTarget {
  val render = s"scala-js $scalaJsVersion ($scalaVersion)"
  val encode = s"_sjs${scalaJsVersion}_$scalaVersion"
  val targetType: ScalaTargetType = Js
}
case class ScalaNative(scalaVersion: SemanticVersion,
                       scalaNativeVersion: SemanticVersion)
    extends ScalaTarget {
  val scalaJsVersion: Option[SemanticVersion] = None
  val render = s"scala-native $scalaNativeVersion ($scalaNativeVersion)"
  val encode = s"_native${scalaNativeVersion}_$scalaVersion"
  val targetType: ScalaTargetType = Native
}

case class SbtPlugin(scalaVersion: SemanticVersion, sbtVersion: SemanticVersion)
    extends ScalaTarget {
  val scalaJsVersion: Option[SemanticVersion] = None
  val render = s"sbt $sbtVersion ($scalaVersion)"
  val encode = s"_${scalaVersion}_$sbtVersion"
  val targetType: ScalaTargetType = Sbt
}

object ScalaTarget extends Parsers {
  // Scala > Js > Native > Sbt
  implicit val ordering: Ordering[ScalaTarget] =
    Ordering.by[ScalaTarget,
                (ScalaTargetType, SemanticVersion, Option[SemanticVersion])] {
      case ScalaJvm(version)           => (Jvm, version, None)
      case ScalaJs(version, jsVersion) => (Js, version, Some(jsVersion))
      case ScalaNative(version, nativeVersion) =>
        (Native, version, Some(nativeVersion))
      case SbtPlugin(version, sbtVersion) => (Sbt, version, Some(sbtVersion))
    }

  def parse(code: String): Option[ScalaTarget] = {
    tryParse(code, x => scalaTargetParser(x))
  }

  private val minScalaVersion = SemanticVersion(2, 10)
  private val maxScalaVersion = SemanticVersion(2, 13)

  def isValidScalaVersion(version: SemanticVersion): Boolean = {
    version >= minScalaVersion && version <= maxScalaVersion
  }

  private def minScalaJsVersion = SemanticVersion(0, 6)

  def isValidScalaJsVersion(version: SemanticVersion): Boolean = {
    minScalaJsVersion <= version
  }

  private def minSbtVersion = SemanticVersion(0, 11)
  private def maxSbtVersion = SemanticVersion(1, 3)

  def isValidSbtVersion(version: SemanticVersion): Boolean = {
    minSbtVersion <= version && version <= maxSbtVersion
  }

  def isValidScalaNativeVersion(version: SemanticVersion): Boolean = true

  import fastparse.NoWhitespace._
  import fastparse._

  private def scalaPart[_: P] = "_" ~ SemanticVersion.Parser

  private def scalaJs[_: P]: P[ScalaTarget] =
    ("_sjs" ~ SemanticVersion.Parser ~ scalaPart).map {
      case (scalaJsVersion, scalaVersion) =>
        ScalaJs(scalaVersion, scalaJsVersion)
    }

  private def scalaNative[_: P]: P[ScalaTarget] =
    ("_native" ~ SemanticVersion.Parser ~ scalaPart).map {
      case (scalaNativeVersion, scalaVersion) =>
        ScalaNative(scalaVersion, scalaNativeVersion)
    }

  private def sbt[_: P]: P[ScalaTarget] =
    (scalaPart ~ "_" ~ SemanticVersion.Parser).map {
      case (scalaVersion, sbtVersion) =>
        SbtPlugin(scalaVersion, sbtVersion)
    }

  private def scala[_: P]: P[ScalaTarget] = scalaPart.map(ScalaJvm)

  def scalaTargetParser[_: P]: P[ScalaTarget] =
    scalaJs | scalaNative | sbt | scala
}
