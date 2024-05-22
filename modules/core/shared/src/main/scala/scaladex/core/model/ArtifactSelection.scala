package scaladex.core.model

case class ArtifactSelection(
    binaryVersion: Option[BinaryVersion],
    artifactNames: Option[Artifact.Name]
) {
  private def filterAll(artifact: Artifact): Boolean =
    binaryVersion.forall(_ == artifact.binaryVersion) && artifactNames.forall(_ == artifact.artifactName)

  def defaultArtifact(artifacts: Seq[Artifact], project: Project): Option[Artifact] = {
    val filteredArtifacts = artifacts.view.filter(filterAll)

    filteredArtifacts.maxByOption { artifact =>
      (
        // default artifact (ex: akka-actors is the default for akka/akka)
        project.settings.defaultArtifact.contains(artifact.artifactName),
        // not deprecated
        !project.settings.deprecatedArtifacts.contains(artifact.artifactName),
        // project repository (ex: shapeless)
        project.repository.value == artifact.artifactName.value,
        // alphabetically
        artifact.artifactName,
        // stable version first
        project.settings.preferStableVersion && artifact.version.preRelease.isDefined,
        artifact.version,
        artifact.binaryVersion
      )
    }(
      Ordering.Tuple7(
        Ordering[Boolean],
        Ordering[Boolean],
        Ordering[Boolean],
        Ordering[Artifact.Name].reverse,
        Ordering[Boolean].reverse,
        Ordering[SemanticVersion],
        Ordering[BinaryVersion]
      )
    )
  }

  def filterArtifacts(artifacts: Seq[Artifact], project: Project): Seq[Artifact] =
    artifacts
      .filter(filterAll)
      .sortBy { artifact =>
        (
          // default artifact (ex: akka-actors is the default for akka/akka)
          project.settings.defaultArtifact.contains(artifact.artifactName),
          // not deprecated
          !project.settings.deprecatedArtifacts.contains(artifact.artifactName),
          // project repository (ex: shapeless)
          project.repository.value == artifact.artifactName.value,
          // alphabetically
          artifact.artifactName,
          // stable version first
          project.settings.preferStableVersion && artifact.version.preRelease.isDefined,
          artifact.version,
          artifact.binaryVersion
        )
      }(
        Ordering
          .Tuple7(
            Ordering[Boolean],
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

object ArtifactSelection {
  def empty = new ArtifactSelection(None, None)
}
