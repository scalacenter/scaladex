package scaladex.core.model

/**
 * Dependency model
 *
 * @param source the maven reference of the source, ex: scaladex
 * @param target the maven reference of one of the dependant libraries of the source, ex: doobie
 */
case class ArtifactDependency(
    source: Artifact.MavenReference,
    target: Artifact.MavenReference,
    scope: String
)

object ArtifactDependency {
  final case class Direct(
      artifactDep: ArtifactDependency,
      target: Option[Artifact]
  ) {
    def url: String = target match {
      case Some(dep) => dep.httpUrl
      case None =>
        s"http://search.maven.org/#artifactdetails|${artifactDep.target.groupId}|${artifactDep.target.artifactId}|${artifactDep.target.version}|jar"
    }

    def name: String = target match {
      case Some(artifact) => artifact.projectRef.toString
      case None =>
        s"${artifactDep.target.groupId}/${artifactDep.target.artifactId}"
    }

    val version: String = artifactDep.target.version

    def isInternal(ref: Project.Reference): Boolean =
      target.exists(_.projectRef == ref)
  }
  object Direct {
    implicit val order: Ordering[Direct] =
      Ordering.by(d => ordering(d.artifactDep, d.name))
  }

  final case class Reverse(
      dependency: ArtifactDependency,
      source: Artifact
  ) {
    def url: String = source.httpUrl
    def name: String = s"${source.projectRef.organization}/${source.artifactName}"
  }

  object Reverse {
    implicit val order: Ordering[Reverse] =
      Ordering.by(d => ordering(d.dependency, d.name))
    def sample(deps: Seq[Reverse], sampleSize: Int): Seq[Reverse] =
      deps
        .groupBy(r => (r.source.projectRef, r.source.artifactId))
        .values
        .map(_.sortBy(_.source.version))
        .map(_.head)
        .toSeq
        .sorted
        .take(sampleSize)
  }

  private def ordering(
      dependency: ArtifactDependency,
      name: String
  ): (Int, String) =
    (
      dependency.scope match {
        case "compile"  => 0
        case "provided" => 1
        case "runtime"  => 2
        case "test"     => 3
        case _          => 4
      },
      name
    )
}
