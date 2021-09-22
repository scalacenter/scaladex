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
  sealed trait Full {
    val dependency: NewDependency
    def url: String
    def name: String
  }
  final case class Direct(
      dependency: NewDependency,
      release: Option[NewRelease]
  ) extends Full {
    def url: String = release match {
      case Some(release) => release.httpUrl
      case None =>
        s"http://search.maven.org/#artifactdetails|${dependency.target.groupId}|${dependency.target.artifactId}|${dependency.target.version}|jar"
    }

    def name: String = release match {
      case Some(release) => s"${release.organization}/${release.artifactName}"
      case None =>
        s"${dependency.target.groupId}/${dependency.target.artifactId}"
    }

    val version: String = dependency.target.version

    def isInternal(ref: Project.Reference): Boolean =
      release.exists(_.projectRef == ref)
  }
  object Direct {
    implicit val order: Ordering[Direct] = Ordering.by(Full.ordering)
  }
  final case class Reverse(
      dependency: NewDependency,
      dependentRelease: NewRelease
  ) extends Full {
    def url: String = dependentRelease.httpUrl
    def name: String =
      s"${dependentRelease.organization}/${dependentRelease.artifactName}"
  }
  object Reverse {
    implicit val order: Ordering[Reverse] = Ordering.by(Full.ordering)
    def sample(deps: Seq[Reverse], sampleSize: Int): Seq[Reverse] = {
      deps
        .groupBy(r =>
          (r.dependentRelease.projectRef, r.dependentRelease.artifactName)
        )
        .values
        .map(_.sortBy(_.dependentRelease.version))
        .map(_.head)
        .toSeq
        .sorted
        .take(sampleSize)
    }
  }
  object Full {
    def ordering: Full => (Int, String) =
      (dep: NewDependency.Full) =>
        (
          dep.dependency.scope match {
            case "compile" => 0
            case "provided" => 1
            case "runtime" => 2
            case "test" => 3
            case _ => 4
          },
          dep.name
        )
  }
}
