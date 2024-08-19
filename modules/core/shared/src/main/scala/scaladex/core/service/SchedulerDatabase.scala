package scaladex.core.service

import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.SemanticVersion

trait SchedulerDatabase extends WebDatabase {
  // project and github
  def getAllProjects(): Future[Seq[Project]]
  def insertProject(project: Project): Future[Unit]
  def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit]
  def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]]

  // project dependencies
  def computeProjectDependencies(reference: Project.Reference, version: SemanticVersion): Future[Seq[ProjectDependency]]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
  def deleteProjectDependencies(ref: Project.Reference): Future[Int]

  // artifacts and its dependencies
  def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] // for init process
  def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit]
  def updateArtifacts(artifacts: Seq[Artifact], newRef: Project.Reference): Future[Int]
  def updateArtifactReleaseDate(reference: MavenReference, releaseDate: Instant): Future[Int]
  def getAllGroupIds(): Future[Seq[Artifact.GroupId]]
  def getAllArtifactIds(ref: Project.Reference): Future[Seq[(Artifact.GroupId, String)]]
  def getAllMavenReferences(): Future[Seq[Artifact.MavenReference]]
  def getDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]]
  def updateLatestVersion(ref: MavenReference): Future[Unit]
}
