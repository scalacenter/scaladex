package ch.epfl.scala.index.server

import java.time.Instant
import java.time.temporal.ChronoUnit

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact._
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Milestone
import scaladex.core.model.PatchBinary
import scaladex.core.model.PreReleaseBinary
import scaladex.core.model.Project
import scaladex.core.model.ReleaseCandidate
import scaladex.core.model.Scala3Version
import scaladex.core.model.ScalaLanguageVersion
import scaladex.core.model.SemanticVersion

object Values {
  val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  // Mock Data for tests
  val artifactId: ArtifactId = ArtifactId.parse("play-json-extra_2.11").get
  val artifact: Artifact = Artifact(
    groupId = GroupId("com.github.xuwei-k"),
    artifactId = artifactId.value,
    version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
    artifactName = artifactId.name,
    platform = artifactId.platform,
    projectRef = Project.Reference.from("xuwei-k", "play-json-extra"),
    description = None,
    releaseDate = None,
    resolver = None,
    licenses = Set(),
    isNonStandardLib = false
  )
  val project: Project = Project(
    artifact.projectRef.organization,
    artifact.projectRef.repository,
    creationDate = Some(now),
    GithubStatus.Ok(now),
    Some(GithubInfo.empty(artifact.projectRef.organization.value, artifact.projectRef.repository.value)),
    Project.Settings.default
  )

  val `3.0.0-M3`: ScalaLanguageVersion = Scala3Version(
    PreReleaseBinary(3, 0, Some(0), Milestone(3))
  )
  val `3.0.0-RC2`: ScalaLanguageVersion = Scala3Version(
    PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(2))
  )
  val `3.0.0-RC1`: ScalaLanguageVersion = Scala3Version(
    PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(1))
  )
  val `3.0.0-RC3`: ScalaLanguageVersion = Scala3Version(
    PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(3))
  )

  val `7.0.0`: SemanticVersion = SemanticVersion(7, 0, 0)
  val `7.1.0`: SemanticVersion = SemanticVersion(7, 1, 0)
  val `7.2.0`: SemanticVersion = SemanticVersion(7, 2, 0)
  val `7.3.0`: SemanticVersion = SemanticVersion(7, 3, 0)

  val nat03: PatchBinary = PatchBinary(0, 3, 0)
  val nat04: PatchBinary = PatchBinary(0, 4, 0)
}
