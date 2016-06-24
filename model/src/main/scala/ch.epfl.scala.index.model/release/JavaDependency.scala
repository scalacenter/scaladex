package ch.epfl.scala.index.model.release

import ch.epfl.scala.index.model.misc.MavenReference

/**
 * java / maven dependency
 * @param dependency contains group- and artifact id
 * @param scope the scope the dependency is used ex: test, compile, runtime
 */
case class JavaDependency(
  dependency: MavenReference,
  scope: Option[Scope]
) extends Dependency
