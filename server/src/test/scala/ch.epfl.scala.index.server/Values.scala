package ch.epfl.scala.index.server

import java.time.Instant
import java.time.temporal.ChronoUnit

import ch.epfl.scala.index.model.Milestone
import ch.epfl.scala.index.model.ReleaseCandidate
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.PatchBinary
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.PreReleaseBinary
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaLanguageVersion
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.Project.Organization
import ch.epfl.scala.index.newModel.Project.Repository

object Values {
  val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  // Mock Data for tests
  val release: NewRelease = NewRelease(
    MavenReference(
      "com.github.xuwei-k",
      "play-json-extra_2.10",
      "0.1.1-play2.3-M1"
    ),
    version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
    organization = Organization("xuwei-k"),
    repository = Repository("play-json-extra"),
    artifactName = ArtifactName("play-json-extra"),
    platform = Platform.ScalaJvm(ScalaVersion.`2.11`),
    description = None,
    releasedAt = None,
    resolver = None,
    licenses = Set(),
    isNonStandardLib = false
  )
  val project: Project = Project(
    release.organization,
    release.repository,
    created = Some(now),
    GithubStatus.Ok(now),
    Some(GithubInfo.empty(release.organization.value, release.repository.value)),
    Project.DataForm.default
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
