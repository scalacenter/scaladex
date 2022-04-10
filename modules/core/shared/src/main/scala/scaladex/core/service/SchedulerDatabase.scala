package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.ReleaseDependency

trait SchedulerDatabase extends WebDatabase {
  def insertProject(project: Project): Future[Unit]
  def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] // for init process
  def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit]
  def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]]
  def getDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]]
  def updateArtifacts(artifacts: Seq[Artifact], newRef: Project.Reference): Future[Int]
  def updateArtifactReleaseDate(reference: MavenReference, releaseDate: Instant): Future[Int]
  def computeProjectDependencies(): Future[Seq[ProjectDependency]]
  def computeReleaseDependencies(): Future[Seq[ReleaseDependency]]
  def updateGithubInfoAndStatus(ref: Project.Reference, githubInfo: GithubInfo, status: GithubStatus): Future[Unit]
  def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]]
  def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
  def insertReleaseDependencies(projectDependencies: Seq[ReleaseDependency]): Future[Int]
  def deleteDependenciesOfMovedProject(): Future[Unit]
  def getAllGroupIds(): Future[Seq[Artifact.GroupId]]
  def getAllMavenReferences(): Future[Seq[Artifact.MavenReference]]
}
