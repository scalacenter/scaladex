package ch.epfl.scala.index.model
package release

import misc.MavenReference

/**
 * java / maven dependency
 * @param dependency contains group- and artifact id
 * @param scope the scope the dependency is used ex: test, compile, runtime
 */
case class JavaDependency(
  dependency: MavenReference,
  scope: Option[Scope]
) extends Dependency
