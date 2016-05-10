package ch.epfl.scala.index

case class ArtifactRef(
  groupId: String,
  artifactId: String,
  version: String
)

case class ISO_8601_Date(value: String)

case class Project(
  groupId: String,
  artifactId: String,
  releases: List[Artifact] // sorted by version desc
)

case class Artifact(
  name: Option[String],
  description: Option[String],
  ref: ArtifactRef,
  releaseDates: List[ISO_8601_Date],
  mavenCentral: Boolean,
  dependencies: Set[ArtifactRef],
  reverseDependencies: Set[ArtifactRef],
  github: Set[GithubRepo],
  licenses: Set[License]
) {
  def sbtInstall = {
    import ref._
    val scalaJs = "_sjs0.6_2.11"
    val scalaBin = "_2.11"
    val full = Set(
      "2.11.0",
      "2.11.1",
      "2.11.2",
      "2.11.3",
      "2.11.5",
      "2.11.6",
      "2.11.7",
      "2.11.8"
    )

    val (op, artifactId2, cross) =
      if(artifactId.endsWith(scalaJs)) ("%%%", artifactId.dropRight(scalaJs.length), "")
      else if(artifactId.endsWith(scalaBin)) ("%%", artifactId.dropRight(scalaBin.length), "")
      else {
        full.find(f => artifactId.endsWith("_" + f)) match {
          case Some(v) => ("%", artifactId.dropRight(("_" + v).length), "cross CrossVersion.full")
          case None => ("%", artifactId, "")
        }
      }

    s""""$groupId" $op "$artifactId2" % "$version" $cross"""
  }
  def scalaDocURI: Option[String] = {
    if(mavenCentral) {
      import ref._
      // no frame
      // hosted on s3 at:
      // https://static.javadoc.io/$groupId/$artifactId/$version/index.html#package
      // HEAD to check 403 vs 200

      Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
    } else None
  }
}

case class GithubRepo(user: String, repo: String)

