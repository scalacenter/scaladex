package ch.epfl.scala.index.model

/**
 * Representation of a scala artifact.
 *
 * @param reference simplified artifact name extracted from the artifactId (ex: typelevel/cats-core)
 * @param releases associated releases with target (scala, scalajs, ...)
 * @param deprecation optional deprecation tag
 *
 * TODO: should we move: name, description, license from release to artifact ?
 */
case class Artifact(
  reference: Artifact.Reference,
  releases: List[Release],
  deprecation: Option[Deprecation] = None
)

object Artifact {

  /**
   * Artifact reference
   * @param organization the organization name ex: typelevel
   * @param name the name of this reference ex: cats-core
   */
  case class Reference(organization: String, name: String)
}