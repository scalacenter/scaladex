package ch.epfl.scala.index

case class ArtifactRef(
  groupId: String,
  artifactId: String,
  version: String
)

case class ISO_8601_Date(value: String)

case class Artifact(
  name: String,
  description: Option[String],
  ref: ArtifactRef,
  releaseDates: List[ISO_8601_Date],
  dependencies: Set[ArtifactRef],
  github: Set[GithubRepo],
  licenses: Set[License]
) {
  def scalaDocURI = {
    import ref._
    s"https://www.javadoc.io/doc/$groupId/$artifactId/$version"
  }
}

case class GithubRepo(user: String, repo: String)