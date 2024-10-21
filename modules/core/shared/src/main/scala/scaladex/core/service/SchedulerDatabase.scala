package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.SemanticVersion

trait SchedulerDatabase extends WebDatabase {
  // project and github
  def getAllProjects(): Future[Seq[Project]]
  def getAllProjectArtifacts(ref: Project.Reference): Future[Seq[Artifact]]
  def insertProject(project: Project): Future[Unit]
  def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit]
  def computeProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]]
  def getProjectDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]]

  // project dependencies
  def computeProjectDependencies(reference: Project.Reference, version: SemanticVersion): Future[Seq[ProjectDependency]]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
  def deleteProjectDependencies(ref: Project.Reference): Future[Int]

  // artifacts and its dependencies
  def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] // for init process
  def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit]
  def updateArtifacts(artifacts: Seq[Artifact.Reference], newRef: Project.Reference): Future[Int]
  def updateArtifactReleaseDate(reference: Artifact.Reference, releaseDate: Instant): Future[Int]
  def getGroupIds(): Future[Seq[Artifact.GroupId]]
  def getArtifactIds(ref: Project.Reference): Future[Seq[(Artifact.GroupId, Artifact.ArtifactId)]]
  def getArtifactRefs(): Future[Seq[Artifact.Reference]]
  def updateLatestVersion(ref: Artifact.Reference): Future[Unit]
}
