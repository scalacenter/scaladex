package ch.epfl.scala.index

case class ArtifactRef(
  groupId: String,
  artifactId: String,
  version: String
)

case class Artifact(
  ref: ArtifactRef,
  dependencies: Set[ArtifactRef],
  github: Set[GithubRepo],
  licenses: Set[License]
)

case class GithubRepo(user: String, repo: String)