package ch.epfl.scala.index.model

import fastparse.all._
import fastparse.core.Parsed

import release.ScalaTarget

object Artifact extends Parsers {
  private val ArtifactNameParser = {
    val ScalaPart = "_" ~ SemanticVersion.Parser

    val ScalaJs: P[ScalaTarget] =
      ("_sjs" ~ SemanticVersion.Parser ~ ScalaPart).map {
        case (scalaJsVersion, scalaVersion) =>
          ScalaTarget.scalaJs(scalaVersion, scalaJsVersion)
      }

    val ScalaNative: P[ScalaTarget] =
      ("_native" ~ SemanticVersion.Parser ~ ScalaPart).map {
        case (scalaNativeVersion, scalaVersion) =>
          ScalaTarget.scalaNative(scalaVersion, scalaNativeVersion)
      }

    val Sbt: P[ScalaTarget] = (ScalaPart ~ "_" ~ SemanticVersion.Parser).map {
      case (scalaVersion, sbtVersion) =>
        ScalaTarget.sbt(scalaVersion, sbtVersion)
    }

    val Scala: P[ScalaTarget] = ScalaPart.map(ScalaTarget.scala)

    val ScalaTargetParser =
      ScalaJs | ScalaNative | Sbt | Scala

    Start ~
      (Alpha | Digit | "-".! | ".".! | (!(ScalaTargetParser ~ End) ~ "_")).rep.! ~ // must end with scala target
      ScalaTargetParser ~
      End
  }

  def apply(artifactId: String): Option[(String, ScalaTarget)] = {
    ArtifactNameParser.parse(artifactId) match {
      case Parsed.Success(v, _) => Some(v)
      case _                    => None
    }
  }
}

/**
 * We distinguish between 3 kinds of artifacts:
 *  - conventional Scala library (whose artifact names are suffixed by the targeted Scala version -- e.g. "_2.11")
 *  - unconventional Scala library (whose artifact names are ''not'' suffixed by the targeted Scala version)
 *  - sbt plugins
 */
sealed trait ArtifactKind

object ArtifactKind {

  case object ConventionalScalaLib extends ArtifactKind
  case object UnconventionalScalaLib extends ArtifactKind
  case object SbtPlugin extends ArtifactKind

}
