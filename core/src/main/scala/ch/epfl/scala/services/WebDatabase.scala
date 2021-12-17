package ch.epfl.scala.services

import java.time.Instant

import scala.concurrent.Future

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DataForm
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.ReleaseDependency

trait WebDatabase {
  def insertRelease(release: NewRelease, dependencies: Seq[ReleaseDependency], time: Instant): Future[Unit]
  def updateProjectForm(ref: NewProject.Reference, dataForm: DataForm): Future[Unit]
  def findProject(projectRef: NewProject.Reference): Future[Option[NewProject]]
  def findReleases(projectRef: NewProject.Reference): Future[Seq[NewRelease]]
  def findReleases(projectRef: NewProject.Reference, artifactName: ArtifactName): Future[Seq[NewRelease]]
  def getMostDependentUponProject(max: Int): Future[List[(NewProject, Long)]]
  def findDirectDependencies(release: NewRelease): Future[List[ReleaseDependency.Direct]]
  def findReverseDependencies(release: NewRelease): Future[List[ReleaseDependency.Reverse]]
  def getAllTopics(): Future[Seq[String]]
  def getAllPlatforms(): Future[Map[NewProject.Reference, Set[Platform]]]
  def getLatestProjects(limit: Int): Future[Seq[NewProject]]
  def countProjects(): Future[Long]
  def countReleases(): Future[Long]
  def countDependencies(): Future[Long]
}
