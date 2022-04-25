package scaladex.core.test

import java.time.Instant
import java.util.UUID

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubStatus
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.ReleaseDependency
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserState
import scaladex.core.model.web.ArtifactsPageParams
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

  override def insertArtifact(
      artifact: Artifact,
      dependencies: Seq[ArtifactDependency],
      now: Instant
  ): Future[Boolean] = {
    val ref = artifact.projectRef
    val isNewProject = !projects.contains(ref)
    if (isNewProject) projects.addOne(ref -> Project.default(ref, now = now))
    artifacts.addOne(ref -> (artifacts.getOrElse(ref, Seq.empty) :+ artifact))
    dependencies.appendedAll(dependencies)
    Future.successful(isNewProject)
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

  override def getArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Artifact]] =
    Future.successful {
      artifacts.values.flatten.filter { artifact: Artifact =>
        artifact.groupId == groupId && artifact.artifactId == artifactId.value
      }.toSeq
    }

  override def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]] =
    Future.successful(artifacts.getOrElse(projectRef, Nil))

  override def getArtifactPlatforms(ref: Project.Reference, artifactName: Artifact.Name): Future[Seq[Platform]] =
    Future.successful {
      artifacts
        .getOrElse(ref, Seq.empty)
        .filter(_.artifactName == artifactName)
        .map(_.platform)
        .distinct
    }

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

  override def getArtifactsByVersion(ref: Project.Reference, version: SemanticVersion): Future[Seq[Artifact]] =
    Future.successful {
      artifacts.getOrElse(ref, Seq.empty).filter(_.version == version)
    }

  override def getArtifactNames(ref: Project.Reference): Future[Seq[Artifact.Name]] = Future.successful(
    artifacts.getOrElse(ref, Nil).map(_.artifactName).distinct
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
    Future.successful(artifacts.values.flatten.toSeq.filter(constraint))
  }

  override def getDirectDependencies(artifact: Artifact): Future[List[ArtifactDependency.Direct]] =
    Future.successful(Nil)

  override def getReverseDependencies(artifact: Artifact): Future[List[ArtifactDependency.Reverse]] =
    Future.successful(Nil)

  override def countArtifacts(): Future[Long] =
    Future.successful(artifacts.values.flatten.size)

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] = ???

  override def getAllProjects(): Future[Seq[Project]] = ???

  override def computeProjectDependencies(): Future[Seq[ProjectDependency]] = ???
  override def computeReleaseDependencies(): Future[Seq[ReleaseDependency]] = ???

  override def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] = ???

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    Future.successful(projects.update(ref, projects(ref).copy(creationDate = Some(creationDate))))

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] = ???

  override def insertReleaseDependencies(releaseDependency: Seq[ReleaseDependency]): Future[Int] = ???

  override def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int] =
    // not really implemented
    Future.successful(0)

  override def updateArtifacts(artifacts: Seq[Artifact], newRef: Project.Reference): Future[Int] = ???
  override def deleteDependenciesOfMovedProject(): scala.concurrent.Future[Unit] = ???
  override def getAllGroupIds(): Future[Seq[Artifact.GroupId]] = ???
  override def getAllMavenReferences(): Future[Seq[Artifact.MavenReference]] = ???
  override def insertSession(userId: UUID, userState: UserState): Future[Unit] = ???
  override def getSession(userId: UUID): Future[Option[UserState]] = ???
  override def getAllSessions(): Future[Seq[(UUID, UserState)]] = ???
  override def deleteSession(userId: UUID): Future[Unit] = ???
  override def updateArtifactReleaseDate(reference: Artifact.MavenReference, releaseDate: Instant): Future[Int] = ???

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    Future.successful(
      projects.update(ref, projects(ref).copy(githubInfo = Some(githubInfo), githubStatus = githubStatus))
    )

  override def updateGithubInfo(
      repo: Project.Reference,
      response: GithubResponse[(Project.Reference, GithubInfo)],
      now: Instant
  ): Future[Unit] =
    response match {
      case GithubResponse.Ok((repo, githubInfo)) =>
        Future.successful(
          projects.update(repo, projects(repo).copy(githubInfo = Some(githubInfo), githubStatus = GithubStatus.Ok(now)))
        )
      case _ => Future.successful(())
    }

  override def getArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      params: ArtifactsPageParams
  ): Future[Seq[Artifact]] =
    // does not filter with params
    Future.successful(artifacts.getOrElse(ref, Seq.empty).filter(_.artifactName == artifactName))
  override def getDirectReleaseDependencies(
      ref: Project.Reference,
      version: SemanticVersion
  ): Future[Seq[ReleaseDependency.Direct]] = Future.successful(Seq.empty)
  override def getReverseReleaseDependencies(ref: Project.Reference): Future[Seq[ReleaseDependency.Reverse]] =
    Future.successful(Seq.empty)
  override def countVersions(ref: Project.Reference): Future[Long] =
    Future.successful {
      artifacts.getOrElse(ref, Seq.empty).map(_.version).distinct.size
    }
  override def getLastVersion(ref: Project.Reference): Future[SemanticVersion] = Future.successful {
    artifacts.getOrElse(ref, Seq.empty).head.version
  }
  override def getArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      version: SemanticVersion
  ): Future[Seq[Artifact]] =
    Future.successful {
      artifacts.getOrElse(ref, Seq.empty).filter(a => a.artifactName == artifactName && a.version == version)
    }
}
