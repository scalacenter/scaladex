package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model.SemanticVersion

trait SonatypeService {
  def getAllArtifactIds(groupId: Artifact.GroupId): Future[Seq[Artifact.ArtifactId]]
  def getAllVersions(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[SemanticVersion]]
  def getPomFile(mavenReference: MavenReference): Future[Option[(String, Instant)]]
  def getReleaseDate(mavenReference: MavenReference): Future[Option[Instant]]
}
