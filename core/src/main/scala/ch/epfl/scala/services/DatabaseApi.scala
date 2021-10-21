package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DataForm
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.index.newModel.ReleaseDependency

trait DatabaseApi {
  def insertProject(p: NewProject): Future[Unit]
  def insertOrUpdateProject(p: NewProject): Future[Unit]
  def updateProjectForm(
      ref: NewProject.Reference,
      dataForm: DataForm
  ): Future[Unit]
  def findProject(projectRef: NewProject.Reference): Future[Option[NewProject]]
  def insertReleases(r: Seq[NewRelease]): Future[Int]
  def findReleases(projectRef: NewProject.Reference): Future[Seq[NewRelease]]
  def findReleases(
      projectRef: NewProject.Reference,
      artifactName: ArtifactName
  ): Future[Seq[NewRelease]]

  def findDirectDependencies(
      release: NewRelease
  ): Future[List[ReleaseDependency.Direct]]
  def findReverseDependencies(
      release: NewRelease
  ): Future[List[ReleaseDependency.Reverse]]
  def getAllTopics(): Future[Seq[String]]
  def getAllPlatforms(): Future[Map[NewProject.Reference, Set[Platform]]]
  def getAllProjectDependencies(): Future[Seq[ProjectDependency]]
  def insertProjectWithDependentUponProjects(
      projectDependencies: Seq[ProjectDependency]
  ): Future[Int]
  def getMostDependentUponProject(max: Int): Future[List[(NewProject, Long)]]
  def updateCreatedIn(ref: NewProject.Reference): Future[Unit]
  def insertDependencies(dependencies: Seq[ReleaseDependency]): Future[Int]
  def countProjects(): Future[Long]
  def countReleases(): Future[Long]
  def countDependencies(): Future[Long]
}
