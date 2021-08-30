package ch.epfl.scala.index.model.release

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release

/**
 * Contains the dependencies and reverse dependencies of a release
 * as it will be shown in the project page
 *
 * @param release The dependent release
 * @param scalaDependencies The scala dependencies of the release
 * @param javaDependencies The java dependencies of the release
 * @param internalDependencies The internal (in the same project) dependencies of the release
 * @param reverseDependencies The reverse dependencies of the project grouped by project and artifact name
 */
case class Dependencies(
    release: Release.Reference,
    scalaDependencies: Seq[ScalaDependency],
    javaDependencies: Seq[JavaDependency],
    internalDependencies: Seq[ScalaDependency],
    reverseDependencies: Map[(Project.Reference, String), Seq[ScalaDependency]]
) {
  import Dependencies._

  def externalDependencyCount: Int =
    scalaDependencies.size + javaDependencies.size

  def reverseDependencySample(size: Int): Seq[ScalaDependency] = {
    reverseDependencies.values
      .map(_.head)
      .take(size)
      .toSeq
      .sorted(reverseDependencyOrder)
  }
}

object Dependencies {

  /**
   * order dependencies by target release, tests are last
   */
  private val dependencyOrder: Ordering[Dependency] = Ordering.by { dep =>
    (
      dep.scope match {
        case Some("compile") => 0
        case Some("provided") => 1
        case Some("runtime") => 2
        case Some("test") => 3
        case _ => 4
      },
      dep.target.name
    )
  }

  /**
   * order dependencies by dependent release
   */
  private val reverseDependencyOrder: Ordering[Dependency] = Ordering.by {
    dep =>
      dep.dependent.name
  }

  def apply(
      release: Release,
      dependencies: Seq[ScalaDependency],
      reverseDependencies: Seq[ScalaDependency]
  ): Dependencies = Dependencies(
    release = release.reference,
    scalaDependencies =
      dependencies.filterNot(_.isInternal).sorted(dependencyOrder),
    javaDependencies = release.javaDependencies.sorted(dependencyOrder),
    internalDependencies =
      dependencies.filter(_.isInternal).sorted(dependencyOrder),
    reverseDependencies = reverseDependencies.groupBy(dep =>
      (dep.dependent.projectReference, dep.dependent.artifact)
    )
  )
  def empty(ref: Release.Reference): Dependencies =
    Dependencies(ref, Nil, Nil, Nil, Map())
}
