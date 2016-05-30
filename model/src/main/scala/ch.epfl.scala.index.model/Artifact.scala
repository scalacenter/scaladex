package ch.epfl.scala.index.model

// typelevel/cats-core
case class Artifact(
  // simplified artifact name extracted from the artifactId (ex: cats-core)
  // ex: typelevel/cats-core
  reference: Artifact.Reference,
  // associated releases with target (scala, scalajs, ...)
  releases: List[Release],

  deprecation: Option[Deprecation] = None

  // should we move: name, description, license from release to artifact ?
)

object Artifact {
  case class Reference(organization: String, name: String)
}

// see deprecated tag
case class Deprecation(
  since: Option[ISO_8601_Date] = None, // ???
  useInstead: Set[Artifact.Reference] = Set()
)