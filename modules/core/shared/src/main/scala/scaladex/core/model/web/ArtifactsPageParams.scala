package scaladex.core.model.web

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion

case class ArtifactsPageParams(
    binaryVersions: Seq[BinaryVersion],
    artifactName: Option[Artifact.Name],
    showNonSemanticVersion: Boolean
) {
  def remove(b: BinaryVersion): ArtifactsPageParams = this.copy(binaryVersions = binaryVersions.filterNot(_ == b))
  def set(artifactName: Artifact.Name): ArtifactsPageParams = this.copy(artifactName = Some(artifactName))
  def set(showNonSemanticVersions: Boolean): ArtifactsPageParams =
    this.copy(showNonSemanticVersion = showNonSemanticVersions)
}
object ArtifactsPageParams {
  def default: ArtifactsPageParams = ArtifactsPageParams(Nil, None, false)
}
