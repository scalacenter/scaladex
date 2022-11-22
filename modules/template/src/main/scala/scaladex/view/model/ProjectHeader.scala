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

final case class ProjectHeader(
    ref: Project.Reference,
    latestArtifacts: Seq[Artifact],
    versionCount: Long,
    defaultArtifactName: Option[Artifact.Name]
) {
  def artifactNames: Seq[Artifact.Name] = latestArtifacts.map(_.artifactName).distinct.sorted
  def languages: Seq[Language] = latestArtifacts.map(_.language).distinct.sorted
  def platforms: Seq[Platform] = latestArtifacts.map(_.platform).distinct.sorted

  def platforms(artifactName: Artifact.Name): Seq[Platform] =
    latestArtifacts.filter(_.artifactName == artifactName).map(_.platform).distinct.sorted

  def defaultVersion: SemanticVersion = getDefaultArtifact(None, None).version

  def artifactsUrl: String = {
    val artifact = getDefaultArtifact(None, None)
    val preReleaseFilter =
      if (artifact.version.isPreRelease || !artifact.version.isSemantic) "?pre-releases=true" else ""
    s"/$ref/artifacts/${artifact.artifactName}$preReleaseFilter"
  }

  def artifactsUrl(language: Language): String = {
    val artifact = getDefaultArtifact(Some(language), None)
    val preReleaseFilter =
      if (artifact.version.isPreRelease || !artifact.version.isSemantic) "&pre-releases=true" else ""
    s"/$ref/artifacts/${artifact.artifactName}?binary-versions=${artifact.binaryVersion.label}$preReleaseFilter"
  }

  def artifactsUrl(platform: Platform): String = {
    val artifact = getDefaultArtifact(None, Some(platform))
    val preReleaseFilter =
      if (artifact.version.isPreRelease || !artifact.version.isSemantic) "&pre-releases=true" else ""
    s"/$ref/artifacts/${artifact.artifactName}?binary-versions=${artifact.binaryVersion.label}$preReleaseFilter"
  }

  def getDefaultArtifact(language: Option[Language], platform: Option[Platform]): Artifact = {
    val candidates = latestArtifacts
      .filter(artifact => language.forall(_ == artifact.language) && platform.forall(_ == artifact.platform))
    val stableCandidates = candidates.filter(artifact => artifact.version.isSemantic && !artifact.version.isPreRelease)

    def byNameStable = stableCandidates
      .filter(a => defaultArtifactName.exists(_ == a.artifactName))
      .sortBy(_.binaryVersion)(BinaryVersion.ordering.reverse)
      .headOption

    def byNamePrerelease = candidates
      .filter(a => defaultArtifactName.exists(_ == a.artifactName))
      .sortBy(_.binaryVersion)(BinaryVersion.ordering.reverse)
      .headOption

    def byVersion(version: SemanticVersion) =
      candidates
        .filter(_.version == version)
        .sortBy(a => (a.binaryVersion, a.artifactName))(
          Ordering.Tuple2(Ordering[BinaryVersion].reverse, Ordering[Artifact.Name])
        )
        .head

    // find version of latest stable artifact then default artifact of that version
    def byLatestVersionStable =
      stableCandidates
        .sortBy(_.releaseDate)
        .lastOption
        .map(a => byVersion(a.version))

    def byLatestVersionPrerelease =
      candidates
        .sortBy(_.releaseDate)
        .lastOption
        .map(a => byVersion(a.version))

    byNameStable
      .orElse(byNamePrerelease)
      .orElse(byLatestVersionStable)
      .orElse(byLatestVersionPrerelease)
      .getOrElse(throw new Exception(s"No default artifact found for $ref with $language and $platform"))
  }

  def scalaVersions: Seq[Scala] = languages.collect { case v: Scala => v }
  def scalaJsVersions: Seq[ScalaJs] = platforms.collect { case v: ScalaJs => v }
  def scalaNativeVersions: Seq[ScalaNative] = platforms.collect { case v: ScalaNative => v }
  def sbtVersions: Seq[SbtPlugin] = platforms.collect { case v: SbtPlugin => v }
  def millVersions: Seq[MillPlugin] = platforms.collect { case v: MillPlugin => v }
}
