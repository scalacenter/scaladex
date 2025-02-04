package scaladex.core.model

/** Dependency model
  *
  * @param source
  *   the maven reference of the source, ex: scaladex
  * @param target
  *   the maven reference of one of the dependant libraries of the source, ex: doobie
  */
case class ArtifactDependency(
    source: Artifact.Reference,
    target: Artifact.Reference,
    scope: ArtifactDependency.Scope
)

object ArtifactDependency:
  final case class Direct(
      artifactDep: ArtifactDependency,
      target: Option[Artifact]
  ):
    def url: String = target.map(_.httpUrl).getOrElse(artifactDep.target.searchUrl)

    def groupIdAndName: String = target match
      case Some(artifact) => artifact.groupIdAndName
      case None => s"${artifactDep.target.groupId}:${artifactDep.target.artifactId}"

    val version: Version = artifactDep.target.version

    def isInternal(ref: Project.Reference): Boolean =
      target.exists(_.projectRef == ref)
  end Direct
  object Direct:
    given ordering: Ordering[Direct] =
      Ordering.by(d => (d.artifactDep.scope, d.groupIdAndName))

  final case class Reverse(
      dependency: ArtifactDependency,
      source: Artifact
  ):
    def url: String = source.httpUrl
    def groupIdAndName: String = source.groupIdAndName
    def version: Version = source.version

  object Reverse:
    given ordering: Ordering[Reverse] =
      Ordering.by(d => (d.dependency.scope, d.groupIdAndName))
    def sample(deps: Seq[Reverse], sampleSize: Int): Seq[Reverse] =
      deps
        .groupBy(r => (r.source.projectRef, r.source.artifactId))
        .values
        .map(_.sortBy(_.source.version))
        .map(_.head)
        .toSeq
        .sorted
        .take(sampleSize)
  end Reverse

  // current values in the database: optional, macro, runtime, compile, provided, test
  case class Scope(value: String) extends AnyVal with Ordered[Scope]:
    override def toString: String = value
    override def compare(that: Scope): Int = Scope.ordering.compare(this, that)

  object Scope:
    given ordering: Ordering[Scope] = Ordering.by:
      case Scope("compile") => 0
      case Scope("provided") => 1
      case Scope("runtime") => 2
      case Scope("test") => 3
      case _ => 4

    val compile: Scope = Scope("compile")
  end Scope
end ArtifactDependency
