package scaladex.core.model



case class ArtifactSelection(
    binaryVersion: Option[BinaryVersion],
    artifactNames: Option[Artifact.Name],
    version: Option[SemanticVersion],
    selected: Option[String]
) {
  private def filterBinaryVersion(artifact: Artifact): Boolean =
    binaryVersion.forall(_ == artifact.binaryVersion)

  private def filterArtifact(artifact: Artifact): Boolean =
    artifactNames.forall(_ == artifact.artifactName)

  private def filterVersion(artifact: Artifact): Boolean =
    version.forall(_ == artifact.version)

  private def filterAll(artifact: Artifact): Boolean =
    filterBinaryVersion(artifact) &&
      filterArtifact(artifact) &&
      filterVersion(artifact)

  def defaultArtifact(
      artifacts: Seq[Artifact],
      project: Project
  ): Option[Artifact] = {
    val artifactView = artifacts.view
    val filteredArtifacts =
      selected match {
        case Some(selected) =>
          if (selected == "binaryVersion") artifactView.filter(filterBinaryVersion)
          else if (selected == "artifact")
            artifactView.filter(filterArtifact)
          else if (selected == "version")
            artifactView.filter(filterVersion)
          else artifactView.filter(filterAll)
        case None => artifactView.filter(filterAll)
      }

    filteredArtifacts.maxByOption { artifact =>
      (
        // default artifact (ex: akka-actors is the default for akka/akka)
        project.settings.defaultArtifact.contains(artifact.artifactName),
        // project repository (ex: shapeless)
        project.repository.value == artifact.artifactName.value,
        // alphabetically
        artifact.artifactName,
        // stable version first
        project.settings.defaultStableVersion && artifact.version.preRelease.isDefined,
        artifact.version,
        artifact.binaryVersion
      )
    }(
      Ordering.Tuple6(
        Ordering[Boolean],
        Ordering[Boolean],
        Ordering[Artifact.Name].reverse,
        Ordering[Boolean].reverse,
        Ordering[SemanticVersion],
        Ordering[BinaryVersion]
      )
    )
  }

  def filterArtifacts(
      artifacts: Seq[Artifact],
      project: Project
  ): Seq[Artifact] = {
    val filteredArtifacts =
      selected match {
        case Some(selected) =>
          if (selected == "binaryVersion") artifacts.filter(filterBinaryVersion)
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
        artifact.version,
        artifact.binaryVersion
      )
    }(
      Ordering
        .Tuple6(
          Ordering[Boolean],
          Ordering[Boolean],
          Ordering[Artifact.Name].reverse,
          Ordering[Boolean].reverse,
          Ordering[SemanticVersion],
          Ordering[BinaryVersion]
        )
        .reverse
    )
  }

}

object ArtifactSelection {
  def parse(
      binaryVersion: Option[String],
      artifactName: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  ): ArtifactSelection =
    new ArtifactSelection(
      binaryVersion.flatMap(BinaryVersion.fromLabel),
      artifactName,
      version.flatMap(SemanticVersion.parse),
      selected
    )

  def empty = new ArtifactSelection(None, None, None, None)
}
