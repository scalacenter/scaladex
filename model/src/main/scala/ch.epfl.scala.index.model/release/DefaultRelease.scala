package ch.epfl.scala.index.model
package release

case class ReleaseSelection(
    target: Option[ScalaTarget],
    artifact: Option[String],
    version: Option[SemanticVersion],
    selected: Option[String]
)

object ReleaseSelection {
  def parse(target: Option[String],
            artifactName: Option[String],
            version: Option[String],
            selected: Option[String]): ReleaseSelection = {

    new ReleaseSelection(
      target.flatMap(ScalaTarget.decode),
      artifactName,
      version.flatMap(SemanticVersion.parse),
      selected
    )
  }
  def empty = new ReleaseSelection(None, None, None, None)
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
            defaultArtifact: Option[String],
            defaultStableVersion: Boolean): Option[ReleaseOptions] = {

    def filterTarget(release: Release): Boolean =
      selection.target
        .map(target => Some(target) == release.reference.target)
        .getOrElse(true)

    def filterArtifact(release: Release): Boolean =
      selection.artifact.map(_ == release.reference.artifact).getOrElse(true)

    def filterVersion(release: Release): Boolean =
      selection.version.map(_ == release.reference.version).getOrElse(true)

    def filterAll =
      releases.filter(
        release =>
          filterTarget(release) &&
            filterArtifact(release) &&
            filterVersion(release)
      )

    val selectedReleases =
      selection.selected match {
        case Some(selected) =>
          if (selected == "target") releases.filter(filterTarget)
          else if (selected == "artifact") releases.filter(filterArtifact)
          else if (selected == "version") releases.filter(filterVersion)
          else filterAll
        case None => filterAll
      }

    // descending ordering for versions
    implicit def ordering = implicitly[Ordering[SemanticVersion]].reverse

    val releasesSorted = selectedReleases.toList.sortBy { release =>
      import release.reference._
      (
        // artifact
        // match default artifact (ex: akka-actors is the default for akka/akka)
        defaultArtifact != Some(artifact), // false < true
        // match project repository (ex: shapeless)
        projectRepository == artifact,
        // alphabetically
        artifact,
        // version
        defaultStableVersion && version.preRelease.isDefined,
        version,
        // target
        // stable jvm targets first
        target.flatMap(_.scalaJsVersion).isDefined,
        target.flatMap(_.scalaVersion.preRelease).isDefined,
        target.map(_.scalaVersion),
        target.flatMap(_.scalaJsVersion)
      )
    }

    releasesSorted.headOption.map { release =>
      val targets = releases
        .map(_.reference.target)
        .toList
        .flatten
        .sortBy(
          target =>
            (target.targetType, target.scalaVersion, target.scalaJsVersion)
        )

      val artifacts = releases.map(_.reference.artifact).toList.sorted
      val versions = releases.map(_.reference.version).toList.sorted

      ReleaseOptions(
        artifacts,
        versions,
        targets,
        release
      )
    }
  }
}
