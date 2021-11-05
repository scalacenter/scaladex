package ch.epfl.scala.index.model
package release

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease

case class ReleaseSelection(
    target: Option[Platform],
    artifact: Option[NewRelease.ArtifactName],
    version: Option[SemanticVersion],
    selected: Option[String]
) {

  def filterTarget(release: Release): Boolean =
    target.forall(_ == release.reference.target)

  def filterTarget(release: NewRelease): Boolean =
    target.forall(_ == release.platform)

  def filterArtifact(release: Release): Boolean =
    artifact.forall(_.value == release.reference.artifact)

  def filterArtifact(release: NewRelease): Boolean =
    artifact.forall(_ == release.artifactName)

  def filterVersion(release: Release): Boolean =
    version.forall(_ == release.reference.version)

  def filterVersion(release: NewRelease): Boolean =
    version.forall(_ == release.version)

  def filterAll(release: Release): Boolean =
    filterTarget(release) &&
      filterArtifact(release) &&
      filterVersion(release)

  def filterAll(release: NewRelease): Boolean =
    filterTarget(release) &&
      filterArtifact(release) &&
      filterVersion(release)
}

object ReleaseSelection {
  def parse(
      platform: Option[String],
      artifactName: Option[NewRelease.ArtifactName],
      version: Option[String],
      selected: Option[String]
  ): ReleaseSelection =
    new ReleaseSelection(
      platform.flatMap(Platform.parse),
      artifactName,
      version.flatMap(SemanticVersion.tryParse),
      selected
    )
  def empty = new ReleaseSelection(None, None, None, None)
}

/**
 * populate dropdowns to select artifact, version and target
 */
case class ReleaseOptions(
    artifacts: List[String],
    versions: List[SemanticVersion],
    targets: List[Platform],
    release: Release
)

object ReleaseOptions {
  def apply(
      projectRepository: String,
      selection: ReleaseSelection,
      releases: Seq[Release],
      defaultArtifact: Option[String],
      defaultStableVersion: Boolean
  ): Option[ReleaseOptions] = {

    val selectedReleases =
      selection.selected match {
        case Some(selected) =>
          if (selected == "target") releases.filter(selection.filterTarget)
          else if (selected == "artifact")
            releases.filter(selection.filterArtifact)
          else if (selected == "version")
            releases.filter(selection.filterVersion)
          else releases.filter(selection.filterAll)
        case None => releases.filter(selection.filterAll)
      }

    val releasesSorted = selectedReleases.sortBy { release =>
      val ref = release.reference
      (
        // default artifact (ex: akka-actors is the default for akka/akka)
        if (defaultArtifact.contains(ref.artifact)) 1 else 0,
        // project repository (ex: shapeless)
        if (projectRepository == ref.artifact) 1 else 0,
        // alphabetically
        ref.artifact,
        // stable version first
        if (defaultStableVersion && ref.version.preRelease.isDefined) 0 else 1,
        // version
        ref.version,
        // target
        ref.target
      )
    }.reverse

    releasesSorted.headOption.map { release =>
      val targets = releases
        .map(_.reference.target)
        .distinct
        .sorted
        .reverse
        .toList

      val artifacts = releases
        .map(_.reference.artifact)
        .distinct
        .sorted
        .toList

      val versions = releases
        .map(_.reference.version)
        .distinct
        .sorted(SemanticVersion.ordering.reverse)
        .toList

      ReleaseOptions(
        artifacts,
        versions,
        targets,
        release
      )
    }
  }
  def filterReleases(
      selection: ReleaseSelection,
      releases: Seq[NewRelease],
      project: NewProject
  ): Seq[NewRelease] = {
    val selectedReleases =
      selection.selected match {
        case Some(selected) =>
          if (selected == "target") releases.filter(selection.filterTarget)
          else if (selected == "artifact")
            releases.filter(selection.filterArtifact)
          else if (selected == "version")
            releases.filter(selection.filterVersion)
          else releases.filter(selection.filterAll)
        case None => releases.filter(selection.filterAll)
      }

    selectedReleases.sortBy { release =>
      (
        // default artifact (ex: akka-actors is the default for akka/akka)
        if (project.dataForm.defaultArtifact.contains(release.artifactName)) 1
        else 0,
        // project repository (ex: shapeless)
        if (project.repository.value == release.artifactName.value) 1 else 0,
        // alphabetically
        release.artifactName.value,
        // stable version first
        if (project.dataForm.defaultStableVersion && release.version.preRelease.isDefined) 0
        else 1,
        // version
        release.version,
        // target
        release.platform
      )
    }.reverse
  }
}
