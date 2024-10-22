package scaladex.core.api

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion

case class ProjectVersionsParams(
    binaryVersions: Seq[BinaryVersion],
    artifactNames: Seq[Artifact.Name],
    stableOnly: Boolean
)
