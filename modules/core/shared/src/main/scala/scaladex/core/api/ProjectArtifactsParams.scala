package scaladex.core.api

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion

final case class ProjectArtifactsParams(
    binaryVersion: Option[BinaryVersion],
    artifactName: Option[Artifact.Name],
    stableOnly: Boolean
)
