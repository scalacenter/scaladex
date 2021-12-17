package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.ProjectDependency

trait SchedulerDatabase extends WebDatabase {
  def getAllProjectRef(): Future[Seq[NewProject.Reference]]
  def getAllProjects(): Future[Seq[NewProject]]
  def updateGithubInfoAndStatus(
      p: NewProject.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit]
  def createMovedProject(
      ref: NewProject.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus.Moved
  ): Future[Unit]
  def updateGithubStatus(ref: NewProject.Reference, githubStatus: GithubStatus): Future[Unit]
  def computeProjectDependencies(): Future[Seq[ProjectDependency]]
  def computeAllProjectsCreationDate(): Future[Seq[(Instant, NewProject.Reference)]]
  def updateProjectCreationDate(ref: NewProject.Reference, creationDate: Instant): Future[Unit]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
  def countInverseProjectDependencies(projectRef: NewProject.Reference): Future[Int]
}
