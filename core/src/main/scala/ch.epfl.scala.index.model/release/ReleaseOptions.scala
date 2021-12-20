package ch.epfl.scala.index.model
package release

import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.Project

case class ReleaseSelection(
    target: Option[Platform],
    artifact: Option[NewRelease.ArtifactName],
    version: Option[SemanticVersion],
    selected: Option[String]
) {
  private def filterTarget(release: NewRelease): Boolean =
    target.forall(_ == release.platform)

  private def filterArtifact(release: NewRelease): Boolean =
    artifact.forall(_ == release.artifactName)

  private def filterVersion(release: NewRelease): Boolean =
    version.forall(_ == release.version)

  private def filterAll(release: NewRelease): Boolean =
    filterTarget(release) &&
      filterArtifact(release) &&
      filterVersion(release)

  def filterReleases(
      releases: Seq[NewRelease],
      project: Project
  ): Seq[NewRelease] = {
    val selectedReleases =
      selected match {
        case Some(selected) =>
          if (selected == "target") releases.filter(filterTarget)
          else if (selected == "artifact")
            releases.filter(filterArtifact)
          else if (selected == "version")
            releases.filter(filterVersion)
          else releases.filter(filterAll)
        case None => releases.filter(filterAll)
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
