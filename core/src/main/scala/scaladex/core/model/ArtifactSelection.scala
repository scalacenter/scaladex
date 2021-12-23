package scaladex.core.model

case class ArtifactSelection(
    target: Option[Platform],
    artifactNames: Option[Artifact.Name],
    version: Option[SemanticVersion],
    selected: Option[String]
) {
  private def filterTarget(release: Artifact): Boolean =
    target.forall(_ == release.platform)

  private def filterArtifact(release: Artifact): Boolean =
    artifactNames.forall(_ == release.artifactName)

  private def filterVersion(release: Artifact): Boolean =
    version.forall(_ == release.version)

  private def filterAll(release: Artifact): Boolean =
    filterTarget(release) &&
      filterArtifact(release) &&
      filterVersion(release)

  def filterReleases(
      releases: Seq[Artifact],
      project: Project
  ): Seq[Artifact] = {
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
        if (project.settings.defaultArtifact.contains(release.artifactName)) 1
        else 0,
        // project repository (ex: shapeless)
        if (project.repository.value == release.artifactName.value) 1 else 0,
        // alphabetically
        release.artifactName.value,
        // stable version first
        if (project.settings.defaultStableVersion && release.version.preRelease.isDefined) 0
        else 1,
        // version
        release.version,
        // target
        release.platform
      )
    }.reverse
  }
}

object ArtifactSelection {
  def parse(
      platform: Option[String],
      artifactName: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  ): ArtifactSelection =
    new ArtifactSelection(
      platform.flatMap(Platform.parse),
      artifactName,
      version.flatMap(SemanticVersion.tryParse),
      selected
    )
  def empty = new ArtifactSelection(None, None, None, None)
}
