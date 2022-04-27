package scaladex.core.service

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.SemanticVersion
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
  def getArtifactsByVersion(ref: Project.Reference, version: SemanticVersion): Future[Seq[Artifact]]
  def getArtifactByMavenReference(mavenRef: Artifact.MavenReference): Future[Option[Artifact]]
  def getAllArtifacts(maybeLanguage: Option[Language], maybePlatform: Option[Platform]): Future[Seq[Artifact]]
  def getArtifactNames(ref: Project.Reference): Future[Seq[Artifact.Name]]
  def getArtifactPlatforms(ref: Project.Reference, artifactName: Artifact.Name): Future[Seq[Platform]]
  def countArtifacts(): Future[Long]

  // artifact dependencies
  def getDirectDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Direct]]
  def getReverseDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Reverse]]

  // projects
  def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit]
  def getAllProjects(): Future[Seq[Project]]
  def getProject(projectRef: Project.Reference): Future[Option[Project]]
  def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]]
  def updateGithubInfo(
      repo: Project.Reference,
      response: GithubResponse[(Project.Reference, GithubInfo)],
      now: Instant
  ): Future[Unit]
  def countVersions(ref: Project.Reference): Future[Long]
  def getLastVersion(ref: Project.Reference, defaultArtifactName: Option[Artifact.Name]): Future[SemanticVersion]

  // project dependencies
  def countProjectDependents(projectRef: Project.Reference): Future[Long]
  def getProjectDependencies(ref: Project.Reference, version: SemanticVersion): Future[Seq[ProjectDependency]]
  def getProjectDependents(ref: Project.Reference): Future[Seq[ProjectDependency]]

  // sessions
  def insertSession(userId: UUID, userState: UserState): Future[Unit]
  def getSession(userId: UUID): Future[Option[UserState]]
  def getAllSessions(): Future[Seq[(UUID, UserState)]]
  def deleteSession(userId: UUID): Future[Unit]
}
