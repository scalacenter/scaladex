package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project

trait WebDatabase {
  def insertArtifact(artifact: Artifact, dependencies: Seq[ArtifactDependency], time: Instant): Future[Unit]
  def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit]
  def getProject(projectRef: Project.Reference): Future[Option[Project]]
  def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]]
  def getArtifactsByName(projectRef: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]]
  def getDirectDependencies(artifact: Artifact): Future[List[ArtifactDependency.Direct]]
  def getReverseDependencies(artifact: Artifact): Future[List[ArtifactDependency.Reverse]]
  def countArtifacts(): Future[Long]
}
