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
      selection.target.forall(
        target => release.reference.target.contains(target)
      )

    def filterArtifact(release: Release): Boolean =
      selection.artifact.forall(_ == release.reference.artifact)

    def filterVersion(release: Release): Boolean =
      selection.version.forall(_ == release.reference.version)

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

    val releasesSorted = selectedReleases.toSeq.view.sortBy { release =>
      val ref = release.reference
      (
        // default artifact (ex: akka-actors is the default for akka/akka)
        if (defaultArtifact.contains(ref.artifact)) 0 else 1,
        // project repository (ex: shapeless)
        if (projectRepository == ref.artifact) 1 else 0,
        // alphabetically
        ref.artifact,
        // version
        ref.version,
        // target
        ref.target
      )
    }.reverse

    releasesSorted.headOption.map { release =>
      val targets = releases.toSeq.view
        .flatMap(_.reference.target)
        .distinct
        .sorted
        .reverse
        .toList

      val artifacts = releases.map(_.reference.artifact).toList.sorted
      val versions = releases
        .map(_.reference.version)
        .toList
        .sorted(SemanticVersion.ordering.reverse)

      ReleaseOptions(
        artifacts,
        versions,
        targets,
        release
      )
    }
  }
}
