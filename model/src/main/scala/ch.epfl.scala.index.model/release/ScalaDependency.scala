package ch.epfl.scala.index.model
package release

/**
  * Scala dependency
  * @param reference the release reference with further information
  * @param scope the scope the dependency is used ex: test, compile, runtime
  */
case class ScalaDependency(
    reference: Release.Reference,
    scope: Option[String]
) extends Dependency
