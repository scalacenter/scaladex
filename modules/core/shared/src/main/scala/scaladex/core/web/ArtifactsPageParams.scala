package scaladex.core.web

import scaladex.core.model.BinaryVersion

case class ArtifactsPageParams(
    binaryVersions: Seq[BinaryVersion],
    preReleases: Boolean
) {
  def binaryVersionsSummary: Option[String] = 
    Option.when(binaryVersions.nonEmpty)(binaryVersions.mkString(", "))
}

object ArtifactsPageParams {
  def empty: ArtifactsPageParams = ArtifactsPageParams(Seq.empty, false)
}
