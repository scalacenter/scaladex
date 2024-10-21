package scaladex.core.model

import java.time.Instant

object ProjectHeader {
  def apply(
      ref: Project.Reference,
      artifacts: Seq[Artifact],
      versionCount: Long,
      defaultArtifactName: Option[Artifact.Name],
      preferStableVersion: Boolean
  ): Option[ProjectHeader] =
    Option.when(artifacts.nonEmpty) {
      new ProjectHeader(ref, artifacts, versionCount, defaultArtifactName, preferStableVersion)
    }
}

final case class ProjectHeader(
    ref: Project.Reference,
    artifacts: Seq[Artifact],
    versionCount: Long,
    defaultArtifactName: Option[Artifact.Name],
    preferStableVersion: Boolean
) {
  lazy val defaultArtifact: Artifact = getDefaultArtifact(None, None)
  lazy val latestVersion: SemanticVersion = defaultArtifact.version
  lazy val latestArtifacts: Seq[Artifact] = artifacts.filter(_.version == latestVersion)
  lazy val latestLanguages: Seq[Language] = latestArtifacts.map(_.language).distinct.sorted
  lazy val latestPlatforms: Seq[Platform] = latestArtifacts.map(_.platform).distinct.sorted

  def allArtifactNames: Seq[Artifact.Name] = artifacts.map(_.name).distinct.sorted
  def platforms(artifactName: Artifact.Name): Seq[Platform] =
    artifacts.filter(_.name == artifactName).map(_.platform).distinct.sorted(Platform.ordering.reverse)

  def versionsUrl: String = artifactsUrl(getDefaultArtifact(None, None), withBinaryVersion = false)

  def versionsUrl(language: Language): String = artifactsUrl(getDefaultArtifact(Some(language), None))

  def versionsUrl(platform: Platform): String = artifactsUrl(getDefaultArtifact(None, Some(platform)))

  private def artifactsUrl(defaultArtifact: Artifact, withBinaryVersion: Boolean = true): String = {
    val preReleaseFilter = Option.when(preferStableVersion && defaultArtifact.version.isStable)("stableOnly=true")
    val binaryVersionFilter = Option.when(withBinaryVersion)(s"binary-version=${defaultArtifact.binaryVersion.value}")
    val filters = preReleaseFilter.toSeq ++ binaryVersionFilter
    val queryParams = if (filters.nonEmpty) "?" + filters.mkString("&") else ""
    s"/$ref/artifacts/${defaultArtifact.name}$queryParams"
  }

  def getDefaultArtifact(language: Option[Language], platform: Option[Platform]): Artifact = {
    val filteredArtifacts = artifacts
      .filter(artifact => language.forall(_ == artifact.language) && platform.forall(_ == artifact.platform))
    val stableArtifacts = filteredArtifacts.filter(_.version.isStable)

    def byName(artifacts: Seq[Artifact]): Option[Artifact] =
      defaultArtifactName.toSeq
        .flatMap(defaultName => artifacts.filter(a => defaultName == a.name))
        .maxByOption(a => (a.binaryVersion, a.releaseDate))

    def ofVersion(version: SemanticVersion): Artifact =
      filteredArtifacts
        .filter(_.version == version)
        .maxBy(a => (a.binaryVersion, a.name, a.releaseDate))(
          Ordering.Tuple3(Ordering[BinaryVersion], Ordering[Artifact.Name].reverse, Ordering[Instant])
        )

    // find version of latest stable artifact then default artifact of that version
    def byLatestVersion(artifacts: Seq[Artifact]): Option[Artifact] =
      artifacts.maxByOption(_.releaseDate).map(a => ofVersion(a.version))

    if (preferStableVersion) {
      byName(stableArtifacts)
        .orElse(byName(filteredArtifacts))
        .orElse(byLatestVersion(stableArtifacts))
        .orElse(byLatestVersion(filteredArtifacts))
        .get
    } else {
      byName(filteredArtifacts).orElse(byLatestVersion(filteredArtifacts)).get
    }
  }

  def latestScalaVersions: Seq[Scala] = latestLanguages.collect { case v: Scala => v }
  def latestScalaJsVersions: Seq[ScalaJs] = latestPlatforms.collect { case v: ScalaJs => v }
  def latestScalaNativeVersions: Seq[ScalaNative] = latestPlatforms.collect { case v: ScalaNative => v }
  def latestSbtVersions: Seq[SbtPlugin] = latestPlatforms.collect { case v: SbtPlugin => v }
  def latestMillVersions: Seq[MillPlugin] = latestPlatforms.collect { case v: MillPlugin => v }
}
