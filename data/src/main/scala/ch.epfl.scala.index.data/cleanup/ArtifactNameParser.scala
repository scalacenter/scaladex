package ch.epfl.scala.index
package data
package cleanup

import ch.epfl.scala.index.model.release.ScalaTargets
import fastparse.all._
import fastparse.core.Parsed

object ArtifactNameParser {
  private val ArtifactNameParser = {
    val SemanticVersioning = SemanticVersionParser.Parser

    val Scala = "_" ~ SemanticVersioning
    val ScalaJs = "_sjs" ~ SemanticVersioning
    
    val ScalaTarget = (ScalaJs.? ~ Scala).map{case (scalaJsVersion, scalaVersion) => 
      ScalaTargets(scalaVersion, scalaJsVersion)
    }

    Start ~
    (Alpha | Digit | "-".! | ".".! | (!(ScalaTarget ~ End) ~ "_")).rep.! ~ // must end with scala target
    ScalaTarget ~
    End
  }
  
  def apply(artifactId: String): Option[(String, ScalaTargets)] = {
    ArtifactNameParser.parse(artifactId) match {
      case Parsed.Success(v, _) => Some(v)
      case _ => None
    }
  }
}