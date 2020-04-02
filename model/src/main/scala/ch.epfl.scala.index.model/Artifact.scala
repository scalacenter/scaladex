package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.release.ScalaTarget.scalaTargetParser
import fastparse._
import release.ScalaTarget

case class Artifact(name: String, target: ScalaTarget)

object Artifact extends Parsers {
  import fastparse.NoWhitespace._

  private def artifactNameParser[_: P] = {
    Start ~
      (Alpha | Digit | "-".! | ".".! | (!(scalaTargetParser ~ End) ~ "_")).rep.! ~ // must end with scala target
      scalaTargetParser ~
      End
  }.map {
    case (name, target) => Artifact(name, target)
  }

  def parse(artifactId: String): Option[Artifact] = {
    tryParse(artifactId, x => artifactNameParser(x))
  }
}
