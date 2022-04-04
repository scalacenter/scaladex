package scaladex.core.service

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.Project
import scaladex.core.model.UserState

trait WebDatabase {
  // insertArtifact return a boolean. It's true if the a new project is inserted, false otherwise
  def insertArtifact(artifact: Artifact, dependencies: Seq[ArtifactDependency], time: Instant): Future[Boolean]
  def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit]
  def getAllProjects(): Future[Seq[Project]]
  def getProject(projectRef: Project.Reference): Future[Option[Project]]
  def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]]
  def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int]
  def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]]
  def getArtifactsByName(projectRef: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]]
  def getArtifactByMavenReference(mavenRef: Artifact.MavenReference): Future[Option[Artifact]]
  def getDirectDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Direct]]
  def getReverseDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Reverse]]
  def countArtifacts(): Future[Long]
  def insertSession(userId: UUID, userState: UserState): Future[Unit]
  def getSession(userId: UUID): Future[Option[UserState]]
  def updateGithubInfo(
      repo: Project.Reference,
      response: GithubResponse[(Project.Reference, GithubInfo)],
      now: Instant
  ): Future[Unit]
}
