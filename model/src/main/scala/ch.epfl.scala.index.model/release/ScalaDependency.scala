package ch.epfl.scala.index.model.release

import ch.epfl.scala.index.model.Release

/**
 * Scala dependency
 * @param dependency the release reference with further information
 * @param scope the scope the dependency is used ex: test, compile, runtime
 */
case class ScalaDependency(
  dependency: Release.Reference,
  scope: Option[Scope]
) extends Dependency
