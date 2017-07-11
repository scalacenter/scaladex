package ch.epfl.scala.index.model
package release

/**
 * Dependency trait to mark a class as a dependency
 */
trait Dependency {
  val reference: GeneralReference
  val scope: Option[String]
}

/**
 * General Reference to Group MavenReference and Release.Reference
 * to a category form simpler usage.
 */
trait GeneralReference {
  def name: String
  def httpUrl: String
}

/**
 * java / maven dependency
 * @param reference contains group- and artifact id
 * @param scope the scope the dependency is used ex: test, compile, runtime
 */
case class JavaDependency(
    reference: MavenReference,
    scope: Option[String]
) extends Dependency

/**
 * Scala dependency
 * @param reference the release reference with further information
 * @param scope the scope the dependency is used ex: test, compile, runtime
 */
case class ScalaDependency(
    reference: Release.Reference,
    scope: Option[String]
) extends Dependency
