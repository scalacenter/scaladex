package scaladex.core.api

import java.time.Instant

import scaladex.core.model.*

final case class ArtifactResponse(
    groupId: Artifact.GroupId,
    artifactId: Artifact.ArtifactId,
    version: Version,
    artifactName: Artifact.Name,
    binaryVersion: BinaryVersion,
    language: Language,
    platform: Platform,
    project: Project.Reference,
    releaseDate: Instant,
    licenses: Seq[License]
)
