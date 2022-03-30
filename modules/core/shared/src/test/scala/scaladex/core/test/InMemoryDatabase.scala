package scaladex.core.test

import java.time.Instant
import java.util.UUID

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.UserState
import scaladex.core.service.SchedulerDatabase

class InMemoryDatabase extends SchedulerDatabase {
  private val projects = mutable.Map[Project.Reference, Project]()
  private val artifacts = mutable.Map[Project.Reference, Seq[Artifact]]()
  private val dependencies = mutable.Buffer[ArtifactDependency]()

  def reset(): Unit = {
    projects.clear()
    artifacts.clear()
    dependencies.clear()
  }

  override def moveProject(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      status: GithubStatus.Moved
  ): Future[Unit] = ???

  override def insertArtifact(
      artifact: Artifact,
      dependencies: Seq[ArtifactDependency],
      now: Instant
  ): Future[Unit] = {
    val ref = artifact.projectRef
    if (!projects.contains(ref)) projects.addOne(ref -> Project.default(ref, now = now))
    artifacts.addOne(ref -> (artifacts.getOrElse(ref, Seq.empty) :+ artifact))
    dependencies.appendedAll(dependencies)
    Future.successful(())
  }

  override def insertProject(project: Project): Future[Unit] = ???

  override def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] = ???

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit] = ???

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] = {
    projects.update(ref, projects(ref).copy(settings = settings))
    Future.successful(())
  }

  override def getProject(projectRef: Project.Reference): Future[Option[Project]] =
    Future.successful(projects.get(projectRef))

  override def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]] =
    Future.successful(artifacts.getOrElse(projectRef, Nil))

  override def getDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]] = ???

  override def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]] = {
    val result = projects.view
      .mapValues(_.githubStatus)
      .collect { case (ref, GithubStatus.Moved(_, `projectRef`)) => ref }
      .toSeq
    Future.successful(result)
  }

  override def getArtifactsByName(projectRef: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]] =
    Future.successful(
      artifacts
        .getOrElse(projectRef, Nil)
        .filter(_.artifactName == artifactName)
    )

  override def getArtifactByMavenReference(mavenRef: Artifact.MavenReference): Future[Option[Artifact]] = ???

  override def getDirectDependencies(artifact: Artifact): Future[List[ArtifactDependency.Direct]] =
    Future.successful(Nil)

  override def getReverseDependencies(artifact: Artifact): Future[List[ArtifactDependency.Reverse]] =
    Future.successful(Nil)

  override def countArtifacts(): Future[Long] =
    Future.successful(artifacts.values.flatten.size)

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] = ???

  override def getAllProjects(): Future[Seq[Project]] = ???

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    Future.successful(
      projects.update(ref, projects(ref).copy(githubInfo = Some(githubInfo), githubStatus = githubStatus))
    )

  override def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): Future[Unit] = ???

  override def computeProjectDependencies(): Future[Seq[ProjectDependency]] = ???

  override def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] = ???

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    Future.successful(projects.update(ref, projects(ref).copy(creationDate = Some(creationDate))))

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] = ???

  override def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int] =
    // not really implemented
    Future.successful(0)

  override def updateArtifacts(artifacts: Seq[Artifact], newRef: Project.Reference): Future[Int] = ???
  override def deleteDependenciesOfMovedProject(): scala.concurrent.Future[Unit] = ???
  override def getAllGroupIds(): Future[Seq[Artifact.GroupId]] = ???
  override def getAllMavenReferences(): Future[Seq[Artifact.MavenReference]] = ???
  override def insertSession(userId: UUID, userState: UserState): Future[Unit] = ???
  override def getSession(userId: UUID): Future[Option[UserState]] = ???
  override def getAllSessions(): Future[Seq[UserState]] = ???
  override def updateArtifactReleaseDate(reference: Artifact.MavenReference, releaseDate: Instant): Future[Int] = ???
  override def getAllMavenReferencesWithNoReleaseDate(): Future[Seq[Artifact.MavenReference]] = ???
}
