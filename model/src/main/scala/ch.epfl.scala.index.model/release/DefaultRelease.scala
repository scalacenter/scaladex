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
    val (artifact, target) = artifactRaw.
      flatMap(raw => Artifact(raw)).
      map{case (a, b) => (Some(a), Some(b))}.
      getOrElse((artifactRaw, None))

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
  def apply(project: Project, selection: ReleaseSelection, releases: List[Release]): Option[ReleaseOptions] = {

    val selectedReleases =
      releases.filter(release =>
        selection.artifact.map(_ == release.reference.artifact).getOrElse(true) &&
        selection.target.map(_ == release.reference.target).getOrElse(true) &&
        selection.version.map(_ == release.reference.version).getOrElse(true)
      )

    // descending ordering for versions
    implicit def ordering = implicitly[Ordering[SemanticVersion]].reverse

    val releasesSorted =
      selectedReleases.sortBy{release => 
        import release.reference._
        (
        // artifact
          
          // match default artifact (ex: akka-actors is the default for akka/akka)
          project.defaultArtifact == Some(artifact),
          
          // match project repository (ex: shapeless)
          project.repository == artifact,
          
          // alphabetically
          artifact,

        // target

          // stable jvm targets first
          !target.scalaJsVersion.isEmpty,
          !target.scalaVersion.preRelease.isEmpty,
          target.scalaVersion,
          target.scalaJsVersion,

        // version
          !version.preRelease.isEmpty,
          version
        )
      }

    releasesSorted.headOption.map{release =>

      val artifacts = releases.map(_.reference.artifact).distinct.sorted
      val releasesForArtifact = releases.filter(_.reference.artifact == release.reference.artifact)

      val versions = releasesForArtifact.map(_.reference.version).distinct.sorted
      val releasesForArtifactVersion = releasesForArtifact.filter(_.reference.version == release.reference.version)
      
      val targets = releasesForArtifactVersion.map(_.reference.target).distinct.sortBy(target =>
        (target.scalaVersion, target.scalaJsVersion)
      )

      ReleaseOptions(
        artifacts,
        versions,
        targets,
        release
      )
    }
  }
}