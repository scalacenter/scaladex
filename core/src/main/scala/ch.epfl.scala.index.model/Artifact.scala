package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.release.Platform
import fastparse._

case class Artifact(name: String, platform: Platform)

object Artifact extends Parsers {
  import fastparse.NoWhitespace._

  private def FullParser[_: P] = {
    Start ~
      (Alpha | Digit | "-" | "." | (!(Platform.IntermediateParser ~ End) ~ "_")).rep.! ~ // must end with scala target
      Platform.Parser ~
      End
  }.map {
    case (name, target) =>
      Artifact(name, target)
  }

  def parse(artifactId: String): Option[Artifact] =
    tryParse(artifactId, x => FullParser(x))
}
