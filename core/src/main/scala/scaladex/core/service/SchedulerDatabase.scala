package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency

trait SchedulerDatabase extends WebDatabase {
  def getAllProjectRef(): Future[Seq[Project.Reference]]
  def getAllProjects(): Future[Seq[Project]]
  def updateReleases(release: Seq[Artifact], newRef: Project.Reference): Future[Int]
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
