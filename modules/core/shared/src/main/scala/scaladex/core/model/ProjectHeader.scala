package scaladex.core.model

import java.time.Instant

object ProjectHeader:
  def apply(
      ref: Project.Reference,
      artifacts: Seq[Artifact],
      defaultArtifactName: Option[Artifact.Name],
      preferStableVersion: Boolean
  ): Option[ProjectHeader] =
    // A header only exists if it has a default artifact, which is the case if artifacts is non-empty.
    resolveDefaultArtifact(artifacts, defaultArtifactName, preferStableVersion, None, None, None)
      .map(defaultArtifact =>
        new ProjectHeader(ref, artifacts, defaultArtifactName, preferStableVersion, defaultArtifact)
      )

  private def resolveDefaultArtifactName(
      artifacts: Seq[Artifact],
      configuredName: Option[Artifact.Name],
      preferStableVersion: Boolean,
      language: Option[Language],
      platform: Option[Platform]
  ): Option[Artifact.Name] =
    val filteredArtifacts = artifacts.filter(a => language.forall(_ == a.language) && platform.forall(_ == a.platform))
    val stableArtifacts = filteredArtifacts.filter(_.version.isStable)

    def ofVersion(version: Version): Option[Artifact.Name] =
      filteredArtifacts
        .filter(_.version == version)
        .maxByOption(a => (a.binaryVersion, a.name, a.releaseDate))(
          Ordering.Tuple3(Ordering[BinaryVersion], Ordering[Artifact.Name].reverse, Ordering[Instant])
        )
        .map(_.name)

    // find version of latest artifact then default artifact of that version
    def byLatestDate(artifacts: Seq[Artifact]): Option[Artifact.Name] =
      artifacts.maxByOption(_.releaseDate).flatMap(a => ofVersion(a.version))

    val configured = configuredName.filter(name => filteredArtifacts.exists(_.name == name))
    if preferStableVersion then configured.orElse(byLatestDate(stableArtifacts)).orElse(byLatestDate(filteredArtifacts))
    else configured.orElse(byLatestDate(filteredArtifacts))
  end resolveDefaultArtifactName

  /** Resolves the default artifact name (resolveDefaultArtifactName), then picks its latest version. Names and versions
    * are resolved separately because artifacts may not share the same versioning. Returns None when no artifact
    * matches.
    */
  private def resolveDefaultArtifact(
      artifacts: Seq[Artifact],
      configuredName: Option[Artifact.Name],
      preferStableVersion: Boolean,
      language: Option[Language],
      platform: Option[Platform],
      artifactName: Option[Artifact.Name]
  ): Option[Artifact] =
    val defaultArtifactName =
      artifactName.orElse(
        resolveDefaultArtifactName(artifacts, configuredName, preferStableVersion, language, platform)
      )
    defaultArtifactName.flatMap { name =>
      val filteredArtifacts = artifacts.filter { a =>
        a.name == name && language.forall(_ == a.language) && platform.forall(_ == a.platform)
      }
      if preferStableVersion then filteredArtifacts.maxByOption(a => (a.version.isStable, a.version))
      else filteredArtifacts.maxByOption(_.version)
    }
  end resolveDefaultArtifact
end ProjectHeader

final case class ProjectHeader private (
    ref: Project.Reference,
    artifacts: Seq[Artifact],
    defaultArtifactName: Option[Artifact.Name],
    preferStableVersion: Boolean,
    defaultArtifact: Artifact
):
  lazy val latestVersion: Version = defaultArtifact.version
  lazy val latestArtifacts: Seq[Artifact] = artifacts.filter(_.version == latestVersion)

  lazy val aggregatedLanguages: Seq[Language] = artifacts.map(_.language).distinct.sorted
  lazy val aggregatedPlatforms: Seq[Platform] = artifacts.map(_.platform).distinct.sorted

  def allArtifactNames: Seq[Artifact.Name] = artifacts.map(_.name).distinct.sorted
  def platforms(artifactName: Artifact.Name): Seq[Platform] =
    artifacts.filter(_.name == artifactName).map(_.platform).distinct.sorted(Platform.ordering.reverse)
  def artifacts(artifactName: Artifact.Name, platform: Platform): Seq[Artifact] =
    artifacts.filter(a => a.name == artifactName && a.platform == platform)

  def versionsUrl: String = artifactsUrl(defaultArtifact, withBinaryVersion = false)

  def versionsUrl(language: Language): String = artifactsUrl(getDefaultArtifact(Some(language), None))

  def versionsUrl(platform: Platform): String = artifactsUrl(getDefaultArtifact(None, Some(platform)))

  private def artifactsUrl(defaultArtifact: Artifact, withBinaryVersion: Boolean = true): String =
    val preReleaseFilter = Option.when(preferStableVersion && defaultArtifact.version.isStable)("stableOnly=true")
    val binaryVersionFilter = Option.when(withBinaryVersion)(s"binary-version=${defaultArtifact.binaryVersion.value}")
    val filters = preReleaseFilter.toSeq ++ binaryVersionFilter
    val queryParams = if filters.nonEmpty then "?" + filters.mkString("&") else ""
    s"/$ref/artifacts/${defaultArtifact.name}$queryParams"

  def getDefaultArtifact0(binaryVersion: Option[BinaryVersion], artifactName: Option[Artifact.Name]): Option[Artifact] =
    ProjectHeader.resolveDefaultArtifact(
      artifacts,
      defaultArtifactName,
      preferStableVersion,
      binaryVersion.map(_.language),
      binaryVersion.map(_.platform),
      artifactName
    )

  // Falls back to the always-present default artifact, so it never fails even for an absent language/platform.
  def getDefaultArtifact(language: Option[Language], platform: Option[Platform]): Artifact =
    ProjectHeader
      .resolveDefaultArtifact(artifacts, defaultArtifactName, preferStableVersion, language, platform, None)
      .getOrElse(defaultArtifact)

  def allScalaVersions: Seq[Scala] = aggregatedLanguages.collect { case v: Scala => v }
  def allScalaJsVersions: Seq[ScalaJs] = aggregatedPlatforms.collect { case v: ScalaJs => v }
  def allScalaNativeVersions: Seq[ScalaNative] = aggregatedPlatforms.collect { case v: ScalaNative => v }
  def allSbtVersions: Seq[SbtPlugin] = aggregatedPlatforms.collect { case v: SbtPlugin => v }
  def allMillVersions: Seq[MillPlugin] = aggregatedPlatforms.collect { case v: MillPlugin => v }
end ProjectHeader
