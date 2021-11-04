package ch.epfl.scala.index.model
package release

case class ReleaseSelection(
    target: Option[ScalaTarget],
    artifact: Option[String],
    version: Option[SemanticVersion],
    selected: Option[String]
) {

  def filterTarget(release: Release): Boolean =
    target.forall(target => release.reference.target.contains(target))

  def filterArtifact(release: Release): Boolean =
    artifact.forall(_ == release.reference.artifact)

  def filterVersion(release: Release): Boolean =
    version.forall(_ == release.reference.version)

  def filterAll(release: Release): Boolean =
    filterTarget(release) &&
      filterArtifact(release) &&
      filterVersion(release)
}

object ReleaseSelection {
  def parse(
      target: Option[String],
      artifactName: Option[String],
      version: Option[String],
      selected: Option[String]
  ): ReleaseSelection =
    new ReleaseSelection(
      target.flatMap(ScalaTarget.parse),
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
    targets: List[ScalaTarget],
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
        .flatMap(_.reference.target)
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
}
