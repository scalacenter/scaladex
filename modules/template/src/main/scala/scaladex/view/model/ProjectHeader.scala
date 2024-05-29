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
      allArtifacts: Seq[Artifact],
      versionCount: Long,
      defaultArtifactName: Option[Artifact.Name],
      preferStableVersion: Boolean
  ): Option[ProjectHeader] =
    Option.when(allArtifacts.nonEmpty) {
      new ProjectHeader(ref, allArtifacts, versionCount, defaultArtifactName, preferStableVersion)
    }
}

final case class ProjectHeader(
    ref: Project.Reference,
    allArtifacts: Seq[Artifact],
    versionCount: Long,
    defaultArtifactName: Option[Artifact.Name],
    preferStableVersion: Boolean
) {
  lazy val defaultArtifact: Artifact = getDefaultArtifact(None, None)
  lazy val latestVersion: SemanticVersion = defaultArtifact.version
  lazy val latestArtifacts: Seq[Artifact] = allArtifacts.filter(_.version == latestVersion)
  lazy val latestLanguages: Seq[Language] = latestArtifacts.map(_.language).distinct.sorted
  lazy val latestPlatforms: Seq[Platform] = latestArtifacts.map(_.platform).distinct.sorted

  def allArtifactNames: Seq[Artifact.Name] = allArtifacts.map(_.artifactName).distinct.sorted
  def platforms(artifactName: Artifact.Name): Seq[Platform] =
    allArtifacts.filter(_.artifactName == artifactName).map(_.platform).distinct.sorted(Platform.ordering.reverse)

  def artifactsUrl: String = artifactsUrl(getDefaultArtifact(None, None), withBinaryVersion = false)

  def artifactsUrl(language: Language): String = artifactsUrl(getDefaultArtifact(Some(language), None))

  def artifactsUrl(platform: Platform): String = artifactsUrl(getDefaultArtifact(None, Some(platform)))

  private def artifactsUrl(defaultArtifact: Artifact, withBinaryVersion: Boolean = true): String = {
    val preReleaseFilter = Option.when(!preferStableVersion || !defaultArtifact.version.isStable)("pre-releases=true")
    val binaryVersionFilter = Option.when(withBinaryVersion)(s"binary-versions=${defaultArtifact.binaryVersion.label}")
    val filters = preReleaseFilter.toSeq ++ binaryVersionFilter
    val queryParams = if (filters.nonEmpty) "?" + filters.mkString("&") else ""
    s"/$ref/artifacts/${defaultArtifact.artifactName}$queryParams"
  }

  def getDefaultArtifact(language: Option[Language], platform: Option[Platform]): Artifact = {
    val artifacts = allArtifacts
      .filter(artifact => language.forall(_ == artifact.language) && platform.forall(_ == artifact.platform))
    val stableArtifacts = artifacts.filter(_.version.isStable)

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

  def latestScalaVersions: Seq[Scala] = latestLanguages.collect { case v: Scala => v }
  def latestScalaJsVersions: Seq[ScalaJs] = latestPlatforms.collect { case v: ScalaJs => v }
  def latestScalaNativeVersions: Seq[ScalaNative] = latestPlatforms.collect { case v: ScalaNative => v }
  def latestSbtVersions: Seq[SbtPlugin] = latestPlatforms.collect { case v: SbtPlugin => v }
  def latestMillVersions: Seq[MillPlugin] = latestPlatforms.collect { case v: MillPlugin => v }
}
