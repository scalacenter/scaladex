package scaladex.core.service

import java.util.UUID

import scaladex.core.model.*

import cats.effect.IO

trait WebDatabase:
  // artifacts
  def insertArtifact(artifact: Artifact): IO[Boolean]
  def getArtifactVersions(
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      stableOnly: Boolean
  ): IO[Seq[Version]]
  def getArtifacts(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): IO[Seq[Artifact]]
  def getArtifact(ref: Artifact.Reference): IO[Option[Artifact]]
  def getLatestArtifact(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): IO[Option[Artifact]]
  // can return more than one artifact, if artifacts are split in several projects
  def getLatestArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): IO[Seq[Artifact]]
  def getAllArtifacts(language: Option[Language], platform: Option[Platform]): IO[Seq[Artifact]]
  def countArtifacts(): IO[Long]

  // artifact dependencies
  def getDirectDependencies(artifact: Artifact): IO[Seq[ArtifactDependency.Direct]]
  def getReverseDependencies(artifact: Artifact, limit: Int, offset: Int): IO[Seq[ArtifactDependency.Reverse]]
  def countReverseDependencies(artifact: Artifact): IO[Long]

  // projects
  def insertProjectRef(ref: Project.Reference, status: GithubStatus): IO[Boolean]
  def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): IO[Unit]
  def getAllProjectsStatuses(): IO[Map[Project.Reference, GithubStatus]]
  def getProject(projectRef: Project.Reference): IO[Option[Project]]
  def getProjectArtifactRefs(ref: Project.Reference, stableOnly: Boolean): IO[Seq[Artifact.Reference]]
  def getProjectArtifactRefs(ref: Project.Reference, name: Artifact.Name): IO[Seq[Artifact.Reference]]
  def getProjectArtifactRefs(ref: Project.Reference, version: Version): IO[Seq[Artifact.Reference]]
  def getProjectArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      stableOnly: Boolean
  ): IO[Seq[Artifact]]
  def getProjectArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      version: Version
  ): IO[Seq[Artifact]]
  def getProjectLatestArtifacts(ref: Project.Reference): IO[Seq[Artifact]]
  def getFormerReferences(projectRef: Project.Reference): IO[Seq[Project.Reference]]
  def countVersions(ref: Project.Reference): IO[Long]

  // Github info and status
  def updateGithubInfoAndStatus(ref: Project.Reference, info: GithubInfo, status: GithubStatus): IO[Unit]
  def updateGithubStatus(ref: Project.Reference, status: GithubStatus): IO[Unit]
  def moveProject(ref: Project.Reference, info: GithubInfo, status: GithubStatus.Moved): IO[Unit]

  // project dependencies
  def countProjectDependents(projectRef: Project.Reference): IO[Long]
  def getProjectDependencies(ref: Project.Reference, version: Version): IO[Seq[ProjectDependency]]
  def getProjectDependents(ref: Project.Reference): IO[Seq[ProjectDependency]]

  // users
  def insertUser(userId: UUID, user: UserInfo): IO[Unit]
  def updateUser(userId: UUID, userState: UserState): IO[Unit]
  def getUser(userId: UUID): IO[Option[UserState]]
  def getAllUsers(): IO[Seq[(UUID, UserInfo)]]
  def deleteUser(userId: UUID): IO[Unit]
end WebDatabase
