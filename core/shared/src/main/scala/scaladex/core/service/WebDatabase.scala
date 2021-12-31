package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Platform
import scaladex.core.model.Project

trait WebDatabase {
  def insertRelease(release: Artifact, dependencies: Seq[ArtifactDependency], time: Instant): Future[Unit]
  def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit]
  def getProject(projectRef: Project.Reference): Future[Option[Project]]
  def getReleases(projectRef: Project.Reference): Future[Seq[Artifact]]
  def getReleasesByName(projectRef: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]]
  def getMostDependedUponProjects(max: Int): Future[List[(Project, Long)]]
  def getDirectDependencies(release: Artifact): Future[List[ArtifactDependency.Direct]]
  def getReverseDependencies(release: Artifact): Future[List[ArtifactDependency.Reverse]]
  def getAllTopics(): Future[Seq[String]]
  def getAllPlatforms(): Future[Map[Project.Reference, Set[Platform]]]
  def getLatestProjects(limit: Int): Future[Seq[Project]]
  def countProjects(): Future[Long]
  def countArtifacts(): Future[Long]
}
