package scaladex.core.web

import scaladex.core.model.BinaryVersion

case class ArtifactsPageParams(
    binaryVersions: Seq[BinaryVersion],
    preReleases: Boolean
) {
  def remove(b: BinaryVersion): ArtifactsPageParams = copy(binaryVersions = binaryVersions.filterNot(_ == b))
  def withPreReleases(preReleases: Boolean): ArtifactsPageParams = copy(preReleases = preReleases)
}

object ArtifactsPageParams {
  def empty: ArtifactsPageParams = ArtifactsPageParams(Seq.empty, false)
}
