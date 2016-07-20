package ch.epfl.scala.index.model

import fastparse.all._
import fastparse.core.Parsed

import release.ScalaTarget

object Artifact extends Parsers {
  private val ArtifactNameParser = {
    val Scala   = "_" ~ SemanticVersion.Parser
    val ScalaJs = "_sjs" ~ SemanticVersion.Parser

    val ScalaTargetParser = (ScalaJs.? ~ Scala).map {
      case (scalaJsVersion, scalaVersion) =>
        ScalaTarget(scalaVersion, scalaJsVersion)
    }

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
