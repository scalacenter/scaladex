package ch.epfl.scala.index.model
package release

/**
  * java / maven dependency
  * @param reference contains group- and artifact id
  * @param scope the scope the dependency is used ex: test, compile, runtime
  */
case class JavaDependency(
    reference: MavenReference,
    scope: Option[String]
) extends Dependency
