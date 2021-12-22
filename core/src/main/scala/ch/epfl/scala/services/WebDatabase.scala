package ch.epfl.scala.services

import java.time.Instant

import scala.concurrent.Future

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.Artifact.Name
import ch.epfl.scala.index.newModel.ArtifactDependency
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.Project.DataForm

trait WebDatabase {
  def insertRelease(release: Artifact, dependencies: Seq[ArtifactDependency], time: Instant): Future[Unit]
  def updateProjectForm(ref: Project.Reference, dataForm: DataForm): Future[Unit]
  def findProject(projectRef: Project.Reference): Future[Option[Project]]
  def findReleases(projectRef: Project.Reference): Future[Seq[Artifact]]
  def findReleases(projectRef: Project.Reference, artifactName: Name): Future[Seq[Artifact]]
  def getMostDependentUponProject(max: Int): Future[List[(Project, Long)]]
  def findDirectDependencies(release: Artifact): Future[List[ArtifactDependency.Direct]]
  def findReverseDependencies(release: Artifact): Future[List[ArtifactDependency.Reverse]]
  def getAllTopics(): Future[Seq[String]]
  def getAllPlatforms(): Future[Map[Project.Reference, Set[Platform]]]
  def getLatestProjects(limit: Int): Future[Seq[Project]]
  def countProjects(): Future[Long]
  def countArtifacts(): Future[Long]
  def countDependencies(): Future[Long]
}
