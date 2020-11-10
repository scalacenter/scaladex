package ch.epfl.scala.index.model

import fastparse._
import release.ScalaTarget

case class Artifact(name: String, target: ScalaTarget)

object Artifact extends Parsers {
  import fastparse.NoWhitespace._

  private def FullParser[_: P] = {
    Start ~
      (Alpha | Digit | "-".! | ".".! | (!(ScalaTarget.Parser ~ End) ~ "_")).rep.! ~ // must end with scala target
      ScalaTarget.Parser ~
      End
  }.map { case (name, target) =>
    Artifact(name, target)
  }

  def parse(artifactId: String): Option[Artifact] = {
    tryParse(artifactId, x => FullParser(x))
  }
}
