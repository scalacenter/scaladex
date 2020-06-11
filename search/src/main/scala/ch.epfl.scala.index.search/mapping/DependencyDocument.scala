package ch.epfl.scala.index.search.mapping

import ch.epfl.scala.index.model.release.ScalaDependency

/**
 * A dependency document as it is stored in elasticsearch
 *
 * @param id The elasticsearch document id
 * @param dependent The dependent release
 * @param target The depended upon release
 * @param scope The ivy scope of the dependency ("test", "compile", ...)
 */
private[search] case class DependencyDocument(
    id: Option[String],
    dependent: ReleaseRef,
    target: ReleaseRef,
    scope: Option[String]
) {
  def toDependency: ScalaDependency =
    ScalaDependency(dependent.toReference, target.toReference, scope)
}

private[search] object DependencyDocument {
  def apply(dependency: ScalaDependency): DependencyDocument =
    DependencyDocument(
      id = None,
      dependent = ReleaseRef(dependency.dependent),
      target = ReleaseRef(dependency.target),
      scope = dependency.scope
    )
}
