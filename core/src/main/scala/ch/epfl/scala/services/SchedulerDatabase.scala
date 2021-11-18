package ch.epfl.scala.services

import java.time.Instant

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.ProjectDependency

trait SchedulerDatabase extends WebDatabase {
  def getAllProjectRef(): Future[Seq[NewProject.Reference]]
  def getAllProjects(): Future[Seq[NewProject]]
  def updateGithubInfo(p: NewProject.Reference, githubInfo: GithubInfo, now: Instant): Future[Unit]
  def computeProjectDependencies(): Future[Seq[ProjectDependency]]
  def updateCreatedInProjects(): Future[Unit]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
  def countInverseProjectDependencies(projectRef: NewProject.Reference): Future[Int]
}
