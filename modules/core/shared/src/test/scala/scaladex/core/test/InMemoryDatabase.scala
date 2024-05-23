package scaladex.core.test

import java.time.Instant
import java.util.UUID

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.ReleaseDependency
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.service.SchedulerDatabase
import scaladex.core.web.ArtifactsPageParams

class InMemoryDatabase extends SchedulerDatabase {
  private val allProjects = mutable.Map[Project.Reference, Project]()
  private val allArtifacts = mutable.Map[Project.Reference, Seq[Artifact]]()
  private val allDependencies = mutable.Buffer[ArtifactDependency]()

  def reset(): Unit = {
    allProjects.clear()
    allArtifacts.clear()
    allDependencies.clear()
  }

  override def insertArtifact(
      artifact: Artifact,
      dependencies: Seq[ArtifactDependency],
      now: Instant
  ): Future[Boolean] = {
    val ref = artifact.projectRef
    val isNewProject = !allProjects.contains(ref)
    if (isNewProject) allProjects.addOne(ref -> Project.default(ref, now = now))
    allArtifacts.addOne(ref -> (allArtifacts.getOrElse(ref, Seq.empty) :+ artifact))
    dependencies.appendedAll(dependencies)
    Future.successful(isNewProject)
  }

  override def insertProject(project: Project): Future[Unit] = ???

  override def insertArtifacts(allArtifacts: Seq[Artifact]): Future[Unit] = ???

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit] = ???

  override def deleteProjectDependencies(ref: Project.Reference): Future[Int] = ???

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] = {
    allProjects.update(ref, allProjects(ref).copy(settings = settings))
    Future.successful(())
  }

  override def getProject(projectRef: Project.Reference): Future[Option[Project]] =
    Future.successful(allProjects.get(projectRef))

  override def getArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Artifact]] =
    Future.successful {
      allArtifacts.values.flatten.filter { artifact: Artifact =>
        artifact.groupId == groupId && artifact.artifactId == artifactId.value
      }.toSeq
    }

  override def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]] =
    Future.successful(allArtifacts.getOrElse(projectRef, Nil))

  override def getDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]] = ???

  override def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]] = {
    val result = allProjects.view
      .mapValues(_.githubStatus)
      .collect { case (ref, GithubStatus.Moved(_, `projectRef`)) => ref }
      .toSeq
    Future.successful(result)
  }

  override def getArtifactsByName(projectRef: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]] =
    Future.successful(
      allArtifacts
        .getOrElse(projectRef, Nil)
        .filter(_.artifactName == artifactName)
    )

  override def getArtifactByMavenReference(mavenRef: Artifact.MavenReference): Future[Option[Artifact]] = ???

  override def getAllArtifacts(
      maybeLanguage: Option[Language],
      maybePlatform: Option[Platform]
  ): Future[Seq[Artifact]] = {
    val constraint = (maybeLanguage, maybePlatform) match {
      case (Some(language), Some(platform)) =>
        (artifact: Artifact) => artifact.language == language && artifact.platform == platform
      case (Some(language), _) =>
        (artifact: Artifact) => artifact.language == language
      case (_, Some(platform)) =>
        (artifact: Artifact) => artifact.platform == platform
      case _ => (_: Artifact) => true
    }
    Future.successful(allArtifacts.values.flatten.toSeq.filter(constraint))
  }

  override def getDirectDependencies(artifact: Artifact): Future[List[ArtifactDependency.Direct]] =
    Future.successful(Nil)

  override def getReverseDependencies(artifact: Artifact): Future[List[ArtifactDependency.Reverse]] =
    Future.successful(Nil)

  override def countArtifacts(): Future[Long] =
    Future.successful(allArtifacts.values.flatten.size)

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] = ???

  override def getAllProjects(): Future[Seq[Project]] = ???

  override def computeProjectDependencies(
      ref: Project.Reference,
      version: SemanticVersion
  ): Future[Seq[ProjectDependency]] = ???
  override def computeReleaseDependencies(): Future[Seq[ReleaseDependency]] = ???

  override def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] = ???

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    Future.successful(allProjects.update(ref, allProjects(ref).copy(creationDate = Some(creationDate))))

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] = ???

  override def insertReleaseDependencies(releaseDependency: Seq[ReleaseDependency]): Future[Int] = ???

  override def countProjectDependents(ref: Project.Reference): Future[Long] =
    Future.successful(0)

  override def updateArtifacts(allArtifacts: Seq[Artifact], newRef: Project.Reference): Future[Int] = ???
  override def getAllGroupIds(): Future[Seq[Artifact.GroupId]] = ???
  override def getAllMavenReferences(): Future[Seq[Artifact.MavenReference]] = ???
  override def insertUser(userId: UUID, userInfo: UserInfo): Future[Unit] = ???
  override def updateUser(userId: UUID, userInfo: UserState): Future[Unit] = ???
  override def getUser(userId: UUID): Future[Option[UserState]] = ???
  override def getAllUsers(): Future[Seq[(UUID, UserInfo)]] = ???
  override def deleteUser(userId: UUID): Future[Unit] = ???
  override def updateArtifactReleaseDate(reference: Artifact.MavenReference, releaseDate: Instant): Future[Int] = ???

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    Future.successful(
      allProjects.update(ref, allProjects(ref).copy(githubInfo = Some(githubInfo), githubStatus = githubStatus))
    )

  override def getArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      params: ArtifactsPageParams
  ): Future[Seq[Artifact]] =
    // does not filter with params
    Future.successful(allArtifacts.getOrElse(ref, Seq.empty).filter(_.artifactName == artifactName))
  override def getProjectDependencies(
      ref: Project.Reference,
      version: SemanticVersion
  ): Future[Seq[ProjectDependency]] =
    Future.successful(Seq.empty)
  override def getProjectDependents(ref: Project.Reference): Future[Seq[ProjectDependency]] =
    Future.successful(Seq.empty)
  override def countVersions(ref: Project.Reference): Future[Long] =
    Future.successful {
      allArtifacts.getOrElse(ref, Seq.empty).map(_.version).distinct.size
    }

  override def getArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      version: SemanticVersion
  ): Future[Seq[Artifact]] =
    Future.successful {
      allArtifacts.getOrElse(ref, Seq.empty).filter(a => a.artifactName == artifactName && a.version == version)
    }

  override def updateGithubStatus(ref: Project.Reference, status: GithubStatus): Future[Unit] =
    Future.successful(
      allProjects.update(ref, allProjects(ref).copy(githubStatus = status))
    )

  override def moveProject(ref: Project.Reference, info: GithubInfo, status: GithubStatus.Moved): Future[Unit] = {
    val projectToMove = allProjects(ref)
    val newProject = projectToMove.copy(
      organization = status.destination.organization,
      repository = status.destination.repository
    )
    allProjects.update(status.destination, newProject)
    allProjects.update(ref, projectToMove.copy(githubStatus = status))
    Future.successful(())
  }

  override def getLatestArtifacts(ref: Project.Reference, preferStableVersion: Boolean): Future[Seq[Artifact]] = {
    val res = allArtifacts(ref)
      .groupBy(a => (a.groupId, a.artifactId))
      .values
      .map(artifacts => artifacts.maxBy(_.releaseDate))
      .toSeq
    Future.successful(res)
  }
}
