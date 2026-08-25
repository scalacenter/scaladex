package scaladex.core.service

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.Version

import cats.effect.IO

trait SchedulerDatabase extends WebDatabase:
  // project and github
  def getAllProjects(): IO[Seq[Project]]
  def getAllProjectArtifacts(ref: Project.Reference): IO[Seq[Artifact]]
  def insertProject(project: Project): IO[Unit]
  def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): IO[Unit]
  def computeProjectsCreationDates(): IO[Seq[(Instant, Project.Reference)]]
  def getProjectDependencies(projectRef: Project.Reference): IO[Seq[ArtifactDependency]]

  // project dependencies
  def computeProjectDependencies(reference: Project.Reference, version: Version): IO[Seq[ProjectDependency]]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): IO[Int]
  def deleteProjectDependencies(ref: Project.Reference): IO[Int]

  // artifacts and its dependencies
  def insertArtifacts(artifacts: Seq[Artifact]): IO[Unit] // for init process
  def insertDependencies(dependencies: Seq[ArtifactDependency]): IO[Unit]
  def updateArtifacts(artifacts: Seq[Artifact.Reference], newRef: Project.Reference): IO[Int]
  def updateArtifactReleaseDate(ref: Artifact.Reference, releaseDate: Instant): IO[Int]
  def getGroupIds(): IO[Seq[Artifact.GroupId]]
  def getGroupIds(limit: Int, offset: Int): IO[Seq[Artifact.GroupId]]
  def getArtifactIds(ref: Project.Reference): IO[Seq[(Artifact.GroupId, Artifact.ArtifactId)]]
  def getArtifactRefs(): IO[Seq[Artifact.Reference]]
  def getArtifactRefs(groupId: Artifact.GroupId): IO[Seq[Artifact.Reference]]
  def getArtifactRefs(groupId: Artifact.GroupId, limit: Int, offset: Int): IO[Seq[Artifact.Reference]]
  def updateLatestVersion(ref: Project.Reference, artifact: Artifact.Reference): IO[Unit]
end SchedulerDatabase
