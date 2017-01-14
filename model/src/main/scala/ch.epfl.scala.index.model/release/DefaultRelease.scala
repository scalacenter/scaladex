package ch.epfl.scala.index.model
package release

case class ReleaseSelection(
    artifact: Option[String],
    version: Option[SemanticVersion],
    target: Option[ScalaTarget]
)

object ReleaseSelection {

  /**
    * @param artifactRaw either an artifact (ex: cats-core) or an artifactId (ex: cats-core_2.11)
    */
  def apply(artifactRaw: Option[String] = None, version: Option[SemanticVersion] = None) = {
    val (artifact, target) = artifactRaw
      .flatMap(raw => Artifact(raw))
      .map { case (a, b) => (Some(a), Some(b)) }
      .getOrElse((artifactRaw, None))

    new ReleaseSelection(artifact, version, target)
  }
}

/**
  * populate dropdowns to select artifact, version and target
  */
case class ReleaseOptions(
    artifacts: List[String],
    versions: List[SemanticVersion],
    targets: List[ScalaTarget],
    release: Release
)

object DefaultRelease {
  def apply(projectRepository: String,
            selection: ReleaseSelection,
            releases: Set[Release],
            defaultStableVersion: Boolean): Option[ReleaseOptions] = {

    val selectedReleases = releases.filter(
      release =>
        selection.artifact.map(_ == release.reference.artifact).getOrElse(true) &&
          selection.target.map(_ == release.reference.target).getOrElse(true) &&
          selection.version.map(_ == release.reference.version).getOrElse(true))

    // descending ordering for versions
    implicit def ordering = implicitly[Ordering[SemanticVersion]].reverse

    val releasesSorted = selectedReleases.toList.sortBy(defaultSort(_, projectRepository, defaultStableVersion))

    releasesSorted.headOption.map { release =>
      val artifacts = releases.map(_.reference.artifact).toList.sorted
      val releasesForArtifact = releases.filter(_.reference.artifact == release.reference.artifact)

      val versions = releasesForArtifact.map(_.reference.version).toList.sorted
      val releasesForArtifactVersion =
        releasesForArtifact.filter(_.reference.version == release.reference.version)

      val targets = releasesForArtifactVersion
        .map(_.reference.target)
        .toList
        .flatten
        .sortBy(target => (target.scalaVersion, target.scalaJsVersion))

      ReleaseOptions(
        artifacts,
        versions,
        targets,
        release
      )
    }
  }

  private def defaultSort(release: Release, projectRepository: String, defaultStableVersion: Boolean) = {
    import release.reference._
    (
      // artifact

      // match artifact with same name as project repository (ex: shapeless)
      projectRepository == artifact,
      // alphabetically
      artifact,

      // target

      // stable jvm targets first
      target.flatMap(_.scalaJsVersion).isDefined,
      target.flatMap(_.scalaVersion.preRelease).isDefined,
      target.map(_.scalaVersion),
      target.flatMap(_.scalaJsVersion),

      // version
      defaultStableVersion && version.preRelease.isDefined,
      version
    )
  }

  def preferredVersionSort(release: Release, preferStableVersion: Boolean) = {
    val reference = release.reference
    (
      // target
      // stable jvm targets first
      reference.target.flatMap(_.scalaJsVersion).isDefined,
      reference.target.flatMap(_.scalaVersion.preRelease).isDefined,
      reference.target.map(_.scalaVersion),
      reference.target.flatMap(_.scalaJsVersion),
      // version
      preferStableVersion && reference.version.preRelease.isDefined,
      reference.version
    )
  }
}
