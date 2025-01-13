package scaladex.core.model

import java.time.Instant

object ProjectHeader:
  def apply(
      ref: Project.Reference,
      artifacts: Seq[Artifact],
      defaultArtifactName: Option[Artifact.Name],
      preferStableVersion: Boolean
  ): Option[ProjectHeader] =
    Option.when(artifacts.nonEmpty) {
      new ProjectHeader(ref, artifacts, defaultArtifactName, preferStableVersion)
    }
end ProjectHeader

final case class ProjectHeader(
    ref: Project.Reference,
    artifacts: Seq[Artifact],
    defaultArtifactName: Option[Artifact.Name],
    preferStableVersion: Boolean
):
  lazy val defaultArtifact: Artifact = getDefaultArtifact(None, None)
  lazy val latestVersion: Version = defaultArtifact.version
  lazy val latestArtifacts: Seq[Artifact] = artifacts.filter(_.version == latestVersion)
  lazy val latestLanguages: Seq[Language] = latestArtifacts.map(_.language).distinct.sorted
  lazy val latestPlatforms: Seq[Platform] = latestArtifacts.map(_.platform).distinct.sorted

  def allArtifactNames: Seq[Artifact.Name] = artifacts.map(_.name).distinct.sorted
  def platforms(artifactName: Artifact.Name): Seq[Platform] =
    artifacts.filter(_.name == artifactName).map(_.platform).distinct.sorted(Platform.ordering.reverse)
  def artifacts(artifactName: Artifact.Name, platform: Platform): Seq[Artifact] =
    artifacts.filter(a => a.name == artifactName && a.platform == platform)

  def versionsUrl: String = artifactsUrl(getDefaultArtifact(None, None), withBinaryVersion = false)

  def versionsUrl(language: Language): String = artifactsUrl(getDefaultArtifact(Some(language), None))

  def versionsUrl(platform: Platform): String = artifactsUrl(getDefaultArtifact(None, Some(platform)))

  private def artifactsUrl(defaultArtifact: Artifact, withBinaryVersion: Boolean = true): String =
    val preReleaseFilter = Option.when(preferStableVersion && defaultArtifact.version.isStable)("stableOnly=true")
    val binaryVersionFilter = Option.when(withBinaryVersion)(s"binary-version=${defaultArtifact.binaryVersion.value}")
    val filters = preReleaseFilter.toSeq ++ binaryVersionFilter
    val queryParams = if filters.nonEmpty then "?" + filters.mkString("&") else ""
    s"/$ref/artifacts/${defaultArtifact.name}$queryParams"

  def getDefaultArtifact0(binaryVersion: Option[BinaryVersion], artifactName: Option[Artifact.Name]): Artifact =
    getDefaultArtifact(binaryVersion.map(_.language), binaryVersion.map(_.platform), artifactName)

  def getDefaultArtifact(language: Option[Language], platform: Option[Platform]): Artifact =
    getDefaultArtifact(language, platform, None)

  /** getDefaultArtifact is split in two steps: first we get the default artifact name and then, the latest version. The
    * reason is, we cannot use the latest version of all artifacts to get the default artifact if they don't share the
    * same versioning. Instead we use the latest release date. But once we have the artifact with the latest release
    * date, we really want to get the latest version of that artifact, which is not necessarily the latest one released
    * because of back-publishing.
    */
  def getDefaultArtifact(
      language: Option[Language],
      platform: Option[Platform],
      artifactName: Option[Artifact.Name]
  ): Artifact =
    val defaultArtifactName = artifactName.getOrElse(getDefaultArtifactName(language, platform))
    val filteredArtifacts = artifacts.filter { a =>
      a.name == defaultArtifactName && language.forall(_ == a.language) && platform.forall(_ == a.platform)
    }
    if preferStableVersion then filteredArtifacts.maxBy(a => (a.version.isStable, a.version))
    else filteredArtifacts.maxBy(_.version)
  end getDefaultArtifact

  private def getDefaultArtifactName(language: Option[Language], platform: Option[Platform]): Artifact.Name =
    val filteredArtifacts = artifacts.filter(a => language.forall(_ == a.language) && platform.forall(_ == a.platform))
    val stableArtifacts = filteredArtifacts.filter(_.version.isStable)

    def ofVersion(version: Version): Artifact.Name =
      filteredArtifacts
        .filter(_.version == version)
        .maxBy(a => (a.binaryVersion, a.name, a.releaseDate))(
          Ordering.Tuple3(Ordering[BinaryVersion], Ordering[Artifact.Name].reverse, Ordering[Instant])
        )
        .name

    // find version of latest artifact then default artifact of that version
    def byLatestDate(artifacts: Seq[Artifact]): Option[Artifact.Name] =
      artifacts.maxByOption(_.releaseDate).map(a => ofVersion(a.version))

    if preferStableVersion then
      defaultArtifactName
        .filter(name => filteredArtifacts.exists(_.name == name))
        .orElse(byLatestDate(stableArtifacts))
        .orElse(byLatestDate(filteredArtifacts))
        .get
    else
      defaultArtifactName
        .filter(name => filteredArtifacts.exists(_.name == name))
        .orElse(byLatestDate(filteredArtifacts))
        .get
    end if
  end getDefaultArtifactName

  def latestScalaVersions: Seq[Scala] = latestLanguages.collect { case v: Scala => v }
  def latestScalaJsVersions: Seq[ScalaJs] = latestPlatforms.collect { case v: ScalaJs => v }
  def latestScalaNativeVersions: Seq[ScalaNative] = latestPlatforms.collect { case v: ScalaNative => v }
  def latestSbtVersions: Seq[SbtPlugin] = latestPlatforms.collect { case v: SbtPlugin => v }
  def latestMillVersions: Seq[MillPlugin] = latestPlatforms.collect { case v: MillPlugin => v }
end ProjectHeader
