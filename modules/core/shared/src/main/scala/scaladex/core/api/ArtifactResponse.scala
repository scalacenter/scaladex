package scaladex.core.api

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.Language
import scaladex.core.model.License
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.SemanticVersion

final case class ArtifactResponse(
    groupId: Artifact.GroupId,
    artifactId: String,
    version: SemanticVersion,
    artifactName: Artifact.Name,
    project: Project.Reference,
    releaseDate: Instant,
    licenses: Seq[License],
    language: Language,
    platform: Platform
)

object ArtifactResponse {
  def apply(artifact: Artifact): ArtifactResponse = ArtifactResponse(
    artifact.groupId,
    artifact.artifactId,
    artifact.version,
    artifact.artifactName,
    artifact.projectRef,
    artifact.releaseDate,
    artifact.licenses.toSeq,
    artifact.language,
    artifact.platform
  )
}
