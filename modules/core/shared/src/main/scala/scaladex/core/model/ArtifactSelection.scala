package scaladex.core.model

case class ArtifactSelection(
    binaryVersion: Option[BinaryVersion],
    artifactNames: Option[Artifact.Name]
) {
  private def filterAll(artifact: Artifact.Reference): Boolean =
    binaryVersion.forall(_ == artifact.binaryVersion) && artifactNames.forall(_ == artifact.name)

  def defaultArtifact(artifacts: Seq[Artifact.Reference], project: Project): Option[Artifact.Reference] = {
    val filteredArtifacts = artifacts.view.filter(filterAll)

    filteredArtifacts.maxByOption { artifact =>
      (
        // default artifact (ex: akka-actors is the default for akka/akka)
        project.settings.defaultArtifact.contains(artifact.name),
        // not deprecated
        !project.settings.deprecatedArtifacts.contains(artifact.name),
        // project repository (ex: shapeless)
        project.repository.value == artifact.name.value,
        // alphabetically
        artifact.name,
        // stable version first
        project.settings.preferStableVersion && !artifact.version.isStable,
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
        Ordering[Version],
        Ordering[BinaryVersion]
      )
    )
  }

  def filterArtifacts(artifacts: Seq[Artifact.Reference], project: Project): Seq[Artifact.Reference] =
    artifacts
      .filter(filterAll)
      .sortBy { artifact =>
        (
          // default artifact (ex: akka-actors is the default for akka/akka)
          project.settings.defaultArtifact.contains(artifact.name),
          // not deprecated
          !project.settings.deprecatedArtifacts.contains(artifact.name),
          // project repository (ex: shapeless)
          project.repository.value == artifact.name.value,
          // alphabetically
          artifact.name,
          // stable version first
          project.settings.preferStableVersion && !artifact.version.isStable,
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
            Ordering[Version],
            Ordering[BinaryVersion]
          )
          .reverse
      )

}

object ArtifactSelection {
  def empty = new ArtifactSelection(None, None)
}
