package ch.epfl.scala.services

import java.time.Instant

import scala.concurrent.Future

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.Project.DataForm
import ch.epfl.scala.index.newModel.ReleaseDependency

trait WebDatabase {
  def insertRelease(release: NewRelease, dependencies: Seq[ReleaseDependency], time: Instant): Future[Unit]
  def updateProjectForm(ref: Project.Reference, dataForm: DataForm): Future[Unit]
  def findProject(projectRef: Project.Reference): Future[Option[Project]]
  def findReleases(projectRef: Project.Reference): Future[Seq[NewRelease]]
  def findReleases(projectRef: Project.Reference, artifactName: ArtifactName): Future[Seq[NewRelease]]
  def getMostDependentUponProject(max: Int): Future[List[(Project, Long)]]
  def findDirectDependencies(release: NewRelease): Future[List[ReleaseDependency.Direct]]
  def findReverseDependencies(release: NewRelease): Future[List[ReleaseDependency.Reverse]]
  def getAllTopics(): Future[Seq[String]]
  def getAllPlatforms(): Future[Map[Project.Reference, Set[Platform]]]
  def getLatestProjects(limit: Int): Future[Seq[Project]]
  def countProjects(): Future[Long]
  def countReleases(): Future[Long]
  def countDependencies(): Future[Long]
}
