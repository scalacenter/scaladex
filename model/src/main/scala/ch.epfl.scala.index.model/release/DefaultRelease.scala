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
      target.flatMap(ScalaTarget.parse),
      artifactName,
      version.flatMap(SemanticVersion.tryParse),
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
            releases: Seq[Release],
            defaultArtifact: Option[String],
            defaultStableVersion: Boolean): Option[ReleaseOptions] = {

    val selectedReleases =
      selection.selected match {
        case Some(selected) =>
          if (selected == "target") releases.filter(filterTarget(selection))
          else if (selected == "artifact")
            releases.filter(filterArtifact(selection))
          else if (selected == "version")
            releases.filter(filterVersion(selection))
          else releases.filter(filterAll(selection))
        case None => releases.filter(filterAll(selection))
      }

    val releasesSorted = selectedReleases.view.sortBy { release =>
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
      val targets = releases.view
        .flatMap(_.reference.target)
        .distinct
        .sorted
        .reverse
        .toList

      val artifacts = releases.view
        .map(_.reference.artifact)
        .distinct
        .sorted
        .toList

      val versions = releases.view
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

  def filterTarget(selection: ReleaseSelection)(release: Release): Boolean =
    selection.target.forall(
      target => release.reference.target.contains(target)
    )

  def filterArtifact(selection: ReleaseSelection)(release: Release): Boolean =
    selection.artifact.forall(_ == release.reference.artifact)

  def filterVersion(selection: ReleaseSelection)(release: Release): Boolean =
    selection.version.forall(_ == release.reference.version)

  def filterAll(selection: ReleaseSelection)(release: Release): Boolean =
    filterTarget(selection)(release) &&
      filterArtifact(selection)(release) &&
      filterVersion(selection)(release)
}
