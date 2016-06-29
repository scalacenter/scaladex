package ch.epfl.scala.index
package data
package cleanup

import model.release._

import fastparse.all._
import fastparse.core.Parsed

object ArtifactNameParser {
  private val ArtifactNameParser = {
    val SemanticVersioning = SemanticVersionParser.Parser

    val Scala = "_" ~ SemanticVersioning
    val ScalaJs = "_sjs" ~ SemanticVersioning
    
    val ScalaTargetParser = (ScalaJs.? ~ Scala).map{case (scalaJsVersion, scalaVersion) => 
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
      case _ => None
    }
  }
}