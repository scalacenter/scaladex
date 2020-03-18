package ch.epfl.scala.index.model

import fastparse._

import release.ScalaTarget

object Artifact extends Parsers {
  import fastparse.NoWhitespace._

  private def ScalaPart[_: P] = "_" ~ SemanticVersion.Parser

  private def ScalaJs[_: P]: P[ScalaTarget] =
    ("_sjs" ~ SemanticVersion.Parser ~ ScalaPart).map {
      case (scalaJsVersion, scalaVersion) =>
        ScalaTarget.scalaJs(scalaVersion, scalaJsVersion)
    }

  private def ScalaNative[_: P]: P[ScalaTarget] =
    ("_native" ~ SemanticVersion.Parser ~ ScalaPart).map {
      case (scalaNativeVersion, scalaVersion) =>
        ScalaTarget.scalaNative(scalaVersion, scalaNativeVersion)
    }

  private def Sbt[_: P]: P[ScalaTarget] =
    (ScalaPart ~ "_" ~ SemanticVersion.Parser).map {
      case (scalaVersion, sbtVersion) =>
        ScalaTarget.sbt(scalaVersion, sbtVersion)
    }

  private def Scala[_: P]: P[ScalaTarget] = ScalaPart.map(ScalaTarget.scala)

  private def ScalaTargetParser[_: P] =
    ScalaJs | ScalaNative | Sbt | Scala

  private def ArtifactNameParser[_: P] = {
    Start ~
      (Alpha | Digit | "-".! | ".".! | (!(ScalaTargetParser ~ End) ~ "_")).rep.! ~ // must end with scala target
      ScalaTargetParser ~
      End
  }

  def apply(artifactId: String): Option[(String, ScalaTarget)] = {
    fastparse.parse(artifactId, x => ArtifactNameParser(x)) match {
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
