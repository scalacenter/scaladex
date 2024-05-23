package scaladex.core.service

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.web.ArtifactsPageParams

trait WebDatabase {
  // artifacts
  // insertArtifact return a boolean. It's true if a new project is inserted, false otherwise
  def insertArtifact(artifact: Artifact, dependencies: Seq[ArtifactDependency], time: Instant): Future[Boolean]
  def getArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Artifact]]
  def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]]
  def getArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      params: ArtifactsPageParams
  ): Future[Seq[Artifact]]
  def getArtifacts(ref: Project.Reference, artifactName: Artifact.Name, version: SemanticVersion): Future[Seq[Artifact]]
  def getArtifactsByName(projectRef: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]]
  def getLatestArtifacts(ref: Project.Reference, preferStableVersions: Boolean): Future[Seq[Artifact]]
  def getArtifactByMavenReference(mavenRef: Artifact.MavenReference): Future[Option[Artifact]]
  def getAllArtifacts(language: Option[Language], platform: Option[Platform]): Future[Seq[Artifact]]
  def countArtifacts(): Future[Long]

  // artifact dependencies
  def getDirectDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Direct]]
  def getReverseDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Reverse]]

  // projects
  def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit]
  def getAllProjects(): Future[Seq[Project]]
  def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]]
  def getProject(projectRef: Project.Reference): Future[Option[Project]]
  def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]]
  def countVersions(ref: Project.Reference): Future[Long]

  // Github info and status
  def updateGithubInfoAndStatus(ref: Project.Reference, info: GithubInfo, status: GithubStatus): Future[Unit]
  def updateGithubStatus(ref: Project.Reference, status: GithubStatus): Future[Unit]
  def moveProject(ref: Project.Reference, info: GithubInfo, status: GithubStatus.Moved): Future[Unit]

  // project dependencies
  def countProjectDependents(projectRef: Project.Reference): Future[Long]
  def getProjectDependencies(ref: Project.Reference, version: SemanticVersion): Future[Seq[ProjectDependency]]
  def getProjectDependents(ref: Project.Reference): Future[Seq[ProjectDependency]]

  // users
  def insertUser(userId: UUID, user: UserInfo): Future[Unit]
  def updateUser(userId: UUID, userState: UserState): Future[Unit]
  def getUser(userId: UUID): Future[Option[UserState]]
  def getAllUsers(): Future[Seq[(UUID, UserInfo)]]
  def deleteUser(userId: UUID): Future[Unit]
}
