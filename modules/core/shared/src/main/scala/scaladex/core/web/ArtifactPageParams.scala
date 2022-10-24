package scaladex.core.web

import scaladex.core.model.BinaryVersion

case class ArtifactPageParams(binaryVersion: Option[BinaryVersion]) {
  override def toString: String =
    binaryVersion.map(bv => s"?binary-versions=$bv").getOrElse("")
}
