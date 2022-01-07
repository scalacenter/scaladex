package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency

trait SchedulerDatabase extends WebDatabase {
  def getAllProjects(): Future[Seq[Project]]
  def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]]
  def updataArtifacts(artifacts: Seq[Artifact], newRef: Project.Reference): Future[Int]
  def updateGithubInfoAndStatus(ref: Project.Reference, githubInfo: GithubInfo, status: GithubStatus): Future[Unit]
  def moveProject(ref: Project.Reference, githubInfo: GithubInfo, status: GithubStatus.Moved): Future[Unit]
  def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): Future[Unit]
  def computeProjectDependencies(): Future[Seq[ProjectDependency]]
  def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]]
  def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
  def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int]
  def deleteMovedProjectFromProjectDependencyTable(): Future[Unit]
}
