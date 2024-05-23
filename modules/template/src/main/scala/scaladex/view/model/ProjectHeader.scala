package scaladex.view.model

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Language
import scaladex.core.model.MillPlugin
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.SemanticVersion

object ProjectHeader {
  def apply(
      ref: Project.Reference,
      latestArtifacts: Seq[Artifact],
      versionCount: Long,
      defaultArtifactName: Option[Artifact.Name],
      preferStableVersion: Boolean
  ): Option[ProjectHeader] =
    Option.when(latestArtifacts.nonEmpty) {
      new ProjectHeader(ref, latestArtifacts, versionCount, defaultArtifactName, preferStableVersion)
    }
}

final case class ProjectHeader(
    ref: Project.Reference,
    latestArtifacts: Seq[Artifact],
    versionCount: Long,
    defaultArtifactName: Option[Artifact.Name],
    preferStableVersion: Boolean
) {
  def artifactNames: Seq[Artifact.Name] = latestArtifacts.map(_.artifactName).distinct.sorted
  def languages: Seq[Language] = latestArtifacts.map(_.language).distinct.sorted
  def platforms: Seq[Platform] = latestArtifacts.map(_.platform).distinct.sorted

  def platforms(artifactName: Artifact.Name): Seq[Platform] =
    latestArtifacts.filter(_.artifactName == artifactName).map(_.platform).distinct.sorted(Platform.ordering.reverse)

  def defaultVersion: SemanticVersion = getDefaultArtifact(None, None).version

  def artifactsUrl: String = artifactsUrl(getDefaultArtifact(None, None))

  def artifactsUrl(language: Language): String = artifactsUrl(getDefaultArtifact(Some(language), None))

  def artifactsUrl(platform: Platform): String = artifactsUrl(getDefaultArtifact(None, Some(platform)))

  private def artifactsUrl(defaultArtifact: Artifact): String = {
    val preReleaseFilter = if (!preferStableVersion || !defaultArtifact.version.isStable) "&pre-releases=true" else ""
    s"/$ref/artifacts/${defaultArtifact.artifactName}?binary-versions=${defaultArtifact.binaryVersion.label}$preReleaseFilter"
  }

  def getDefaultArtifact(language: Option[Language], platform: Option[Platform]): Artifact = {
    val artifacts = latestArtifacts
      .filter(artifact => language.forall(_ == artifact.language) && platform.forall(_ == artifact.platform))
    val stableArtifacts = latestArtifacts.filter(_.version.isStable)

    def byName(artifacts: Seq[Artifact]): Option[Artifact] =
      defaultArtifactName.toSeq
        .flatMap(defaultName => artifacts.filter(a => defaultName == a.artifactName))
        .maxByOption(_.binaryVersion)

    def ofVersion(version: SemanticVersion): Artifact =
      artifacts
        .filter(_.version == version)
        .maxBy(a => (a.binaryVersion, a.artifactName))(
          Ordering.Tuple2(Ordering[BinaryVersion], Ordering[Artifact.Name].reverse)
        )

    // find version of latest stable artifact then default artifact of that version
    def byLatestVersion(artifacts: Seq[Artifact]): Option[Artifact] =
      artifacts.sortBy(_.releaseDate).lastOption.map(a => ofVersion(a.version))

    if (preferStableVersion) {
      byName(stableArtifacts)
        .orElse(byName(artifacts))
        .orElse(byLatestVersion(stableArtifacts))
        .orElse(byLatestVersion(artifacts))
        .get
    } else {
      byName(artifacts).orElse(byLatestVersion(artifacts)).get
    }
  }

  def scalaVersions: Seq[Scala] = languages.collect { case v: Scala => v }
  def scalaJsVersions: Seq[ScalaJs] = platforms.collect { case v: ScalaJs => v }
  def scalaNativeVersions: Seq[ScalaNative] = platforms.collect { case v: ScalaNative => v }
  def sbtVersions: Seq[SbtPlugin] = platforms.collect { case v: SbtPlugin => v }
  def millVersions: Seq[MillPlugin] = platforms.collect { case v: MillPlugin => v }
}
