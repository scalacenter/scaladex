package scaladex.core.model

case class ArtifactSelection(
    target: Option[Platform],
    artifactNames: Option[Artifact.Name],
    version: Option[SemanticVersion],
    selected: Option[String]
) {
  private def filterTarget(artifact: Artifact): Boolean =
    target.forall(_ == artifact.platform)

  private def filterArtifact(artifact: Artifact): Boolean =
    artifactNames.forall(_ == artifact.artifactName)

  private def filterVersion(artifact: Artifact): Boolean =
    version.forall(_ == artifact.version)

  private def filterAll(artifact: Artifact): Boolean =
    filterTarget(artifact) &&
      filterArtifact(artifact) &&
      filterVersion(artifact)

  def filterArtifacts(
      artifacts: Seq[Artifact],
      project: Project
  ): Seq[Artifact] = {
    val filteredArtifacts =
      selected match {
        case Some(selected) =>
          if (selected == "target") artifacts.filter(filterTarget)
          else if (selected == "artifact")
            artifacts.filter(filterArtifact)
          else if (selected == "version")
            artifacts.filter(filterVersion)
          else artifacts.filter(filterAll)
        case None => artifacts.filter(filterAll)
      }

    filteredArtifacts.sortBy { artifact =>
      (
        // default artifact (ex: akka-actors is the default for akka/akka)
        project.settings.defaultArtifact.contains(artifact.artifactName),
        // project repository (ex: shapeless)
        project.repository.value == artifact.artifactName.value,
        // alphabetically
        artifact.artifactName,
        // stable version first
        project.settings.defaultStableVersion && artifact.version.preRelease.isDefined,
        // version
        artifact.version,
        // target
        artifact.platform
      )
    }(
      Ordering.Tuple6(
        Ordering[Boolean],
        Ordering[Boolean],
        Ordering[Artifact.Name].reverse,
        Ordering[Boolean].reverse,
        Ordering[SemanticVersion],
        Ordering[Platform]
      )
    ).reverse
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
