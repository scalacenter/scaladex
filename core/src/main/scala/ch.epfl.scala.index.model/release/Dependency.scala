package ch.epfl.scala.index.model
package release

sealed trait Dependency {
  val dependent: Release.Reference
  val target: GeneralReference
  val scope: Option[String]
}

/**
 * Reference to either a MavenReference or a Release.Reference
 */
trait GeneralReference {
  def name: String
  def httpUrl: String
}

/**
 * java / maven dependency
 * A dependency toward a reference that is not indexed in Scaladex
 * @param dependent The dependent release
 * @param target The depended upon Maven release
 * @param scope The ivy scope of the dependency: test, compile, runtime...
 */
case class JavaDependency(
    dependent: Release.Reference,
    target: MavenReference,
    scope: Option[String]
) extends Dependency

/**
 * Scala dependency
 * @param dependent The dependent release
 * @param target The dependended upon release reference
 * @param scope The ivy scope of the dependency: test, compile, runtime...
 */
case class ScalaDependency(
    dependent: Release.Reference,
    target: Release.Reference,
    scope: Option[String]
) extends Dependency {
  def isInternal: Boolean =
    dependent.projectReference == target.projectReference
}
