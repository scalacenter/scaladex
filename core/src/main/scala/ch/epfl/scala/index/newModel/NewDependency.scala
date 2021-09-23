package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.release.MavenReference

/**
 * Dependency model
 *
 * @param source the maven reference of the source, ex: scaladex
 * @param target the maven reference of one of the dependant libraries of the source, ex: doobie
 */
case class NewDependency(
    source: MavenReference,
    target: MavenReference,
    scope: String
)

object NewDependency {
  final case class Direct(
      dependency: NewDependency,
      target: Option[NewRelease]
  ) {
    def url: String = target match {
      case Some(release) => release.httpUrl
      case None =>
        s"http://search.maven.org/#artifactdetails|${dependency.target.groupId}|${dependency.target.artifactId}|${dependency.target.version}|jar"
    }

    def name: String = target match {
      case Some(release) => s"${release.organization}/${release.artifactName}"
      case None =>
        s"${dependency.target.groupId}/${dependency.target.artifactId}"
    }

    val version: String = dependency.target.version

    def isInternal(ref: Project.Reference): Boolean =
      target.exists(_.projectRef == ref)
  }
  object Direct {
    implicit val order: Ordering[Direct] =
      Ordering.by(d => ordering(d.dependency, d.name))
  }

  final case class Reverse(
      dependency: NewDependency,
      source: NewRelease
  ) {
    def url: String = source.httpUrl
    def name: String =
      s"${source.organization}/${source.artifactName}"
  }

  object Reverse {
    implicit val order: Ordering[Reverse] =
      Ordering.by(d => ordering(d.dependency, d.name))
    def sample(deps: Seq[Reverse], sampleSize: Int): Seq[Reverse] = {
      deps
        .groupBy(r => (r.source.projectRef, r.source.artifactName))
        .values
        .map(_.sortBy(_.source.version))
        .map(_.head)
        .toSeq
        .sorted
        .take(sampleSize)
    }
  }

  private def ordering(dependency: NewDependency, name: String): (Int, String) =
    (
      dependency.scope match {
        case "compile" => 0
        case "provided" => 1
        case "runtime" => 2
        case "test" => 3
        case _ => 4
      },
      name
    )
}
