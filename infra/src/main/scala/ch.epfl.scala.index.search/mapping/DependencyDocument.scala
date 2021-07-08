package ch.epfl.scala.index.search.mapping

import ch.epfl.scala.index.model.release.ScalaDependency
import ch.epfl.scala.index.model.Release

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
    dependent: Release.Reference,
    dependentUrl: String,
    target: Release.Reference,
    targetUrl: String,
    scope: Option[String]
) {
  def toDependency: ScalaDependency =
    ScalaDependency(dependent, target, scope)
}

private[search] object DependencyDocument {
  def apply(dependency: ScalaDependency): DependencyDocument =
    DependencyDocument(
      id = None,
      dependent = dependency.dependent,
      dependentUrl = dependency.dependent.httpUrl,
      target = dependency.target,
      targetUrl = dependency.target.httpUrl,
      scope = dependency.scope
    )
}
