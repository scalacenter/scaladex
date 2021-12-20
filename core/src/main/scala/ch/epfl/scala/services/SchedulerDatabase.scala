package ch.epfl.scala.services

import java.time.Instant

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.ProjectDependency

trait SchedulerDatabase extends WebDatabase {
  def insertOrUpdateProject(p: Project): Future[Unit]
  def getAllProjectRef(): Future[Seq[Project.Reference]]
  def getAllProjects(): Future[Seq[Project]]
  def updateReleases(release: Seq[NewRelease], newRef: Project.Reference): Future[Int]
  def updateGithubInfoAndStatus(
      p: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit]
  def createMovedProject(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus.Moved
  ): Future[Unit]
  def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): Future[Unit]
  def computeProjectDependencies(): Future[Seq[ProjectDependency]]
  def computeAllProjectsCreationDate(): Future[Seq[(Instant, Project.Reference)]]
  def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
  def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int]
}
