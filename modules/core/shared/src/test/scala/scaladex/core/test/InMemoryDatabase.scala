package scaladex.core.test

import java.time.Instant
import java.util.UUID

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.*
import scaladex.core.service.SchedulerDatabase

class InMemoryDatabase extends SchedulerDatabase:

  private val allProjects = mutable.Map[Project.Reference, Project]()
  private val allArtifacts = mutable.Map[Artifact.Reference, Artifact]()
  private val allDependencies = mutable.Buffer[ArtifactDependency]()
  private val latestArtifacts = mutable.Map[(Artifact.GroupId, Artifact.ArtifactId), Artifact.Reference]()

  def reset(): Unit =
    allProjects.clear()
    allArtifacts.clear()
    allDependencies.clear()

  override def insertArtifact(artifact: Artifact): Future[Boolean] =
    val isNewArtifact = !allArtifacts.contains(artifact.reference)
    allArtifacts += artifact.reference -> artifact
    Future.successful(isNewArtifact)

  override def insertProjectRef(ref: Project.Reference, status: GithubStatus): Future[Boolean] =
    val isNewProject = !allProjects.contains(ref)
    if isNewProject then allProjects.addOne(ref -> Project.default(ref, status))
    Future.successful(isNewProject)

  override def insertProject(project: Project): Future[Unit] =
    allProjects += project.reference -> project
    Future.unit

  override def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] =
    artifacts.foreach(a => allArtifacts += a.reference -> a)
    Future.unit

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit] =
    allDependencies ++= dependencies
    Future.unit

  override def deleteProjectDependencies(ref: Project.Reference): Future[Int] = ???

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] =
    allProjects.update(ref, allProjects(ref).copy(settings = settings))
    Future.unit

  override def getProject(projectRef: Project.Reference): Future[Option[Project]] =
    Future.successful(allProjects.get(projectRef))

  override def getArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Artifact]] =
    val res = allArtifacts.values.filter(a => a.groupId == groupId && a.artifactId == artifactId).toSeq
    Future.successful(res)

  override def getAllProjectArtifacts(ref: Project.Reference): Future[Seq[Artifact]] =
    Future.successful(getProjectArtifactsSync(ref))

  private def getProjectArtifactsSync(ref: Project.Reference): Seq[Artifact] =
    allArtifacts.values.filter(_.projectRef == ref).toSeq

  override def getProjectArtifactRefs(
      ref: Project.Reference,
      stableOnly: Boolean
  ): Future[Seq[Artifact.Reference]] =
    Future.successful(getProjectArtifactsSync(ref).map(_.reference))

  override def getProjectArtifactRefs(ref: Project.Reference, name: Artifact.Name): Future[Seq[Artifact.Reference]] =
    Future.successful(getProjectArtifactsSync(ref).map(_.reference).filter(_.name == name))

  override def getProjectArtifactRefs(
      ref: Project.Reference,
      version: Version
  ): Future[Seq[Artifact.Reference]] =
    Future.successful(getProjectArtifactsSync(ref).map(_.reference).filter(_.version == version))

  override def getProjectDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]] = ???

  override def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]] =
    val result = allProjects.view
      .mapValues(_.githubStatus)
      .collect { case (ref, GithubStatus.Moved(_, `projectRef`)) => ref }
      .toSeq
    Future.successful(result)

  override def getProjectArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      stableOnly: Boolean
  ): Future[Seq[Artifact]] =
    val res = getProjectArtifactsSync(ref).filter(a => a.name == artifactName && (!stableOnly || a.version.isStable))
    Future.successful(res)

  override def getProjectArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      version: Version
  ): Future[Seq[Artifact]] =
    Future.successful(
      getProjectArtifactsSync(ref).filter(a => a.name == artifactName && a.version == version)
    )

  override def getArtifact(ref: Artifact.Reference): Future[Option[Artifact]] =
    Future.successful(allArtifacts.get(ref))

  override def getAllArtifacts(
      maybeLanguage: Option[Language],
      maybePlatform: Option[Platform]
  ): Future[Seq[Artifact]] =
    val constraint = (maybeLanguage, maybePlatform) match
      case (Some(language), Some(platform)) =>
        (artifact: Artifact) => artifact.language == language && artifact.platform == platform
      case (Some(language), _) => (artifact: Artifact) => artifact.language == language
      case (_, Some(platform)) => (artifact: Artifact) => artifact.platform == platform
      case _ => (_: Artifact) => true
    Future.successful(allArtifacts.values.toSeq.filter(constraint))
  end getAllArtifacts

  override def getDirectDependencies(artifact: Artifact): Future[List[ArtifactDependency.Direct]] =
    Future.successful(Nil)

  override def getReverseDependencies(artifact: Artifact): Future[List[ArtifactDependency.Reverse]] =
    Future.successful(Nil)

  override def countArtifacts(): Future[Long] =
    Future.successful(allArtifacts.size)

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] =
    Future.successful(allProjects.view.mapValues(p => p.githubStatus).toMap)

  override def getAllProjects(): Future[Seq[Project]] =
    Future.successful(allProjects.values.toSeq)

  override def computeProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): Future[Seq[ProjectDependency]] = ???

  override def computeProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] = ???

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    Future.successful(allProjects.update(ref, allProjects(ref).copy(creationDate = Some(creationDate))))

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] = ???

  override def countProjectDependents(ref: Project.Reference): Future[Long] =
    Future.successful(0)

  override def updateArtifacts(allArtifacts: Seq[Artifact.Reference], newRef: Project.Reference): Future[Int] = ???
  override def getGroupIds(): Future[Seq[Artifact.GroupId]] = ???
  override def getArtifactRefs(): Future[Seq[Artifact.Reference]] = ???
  override def insertUser(userId: UUID, userInfo: UserInfo): Future[Unit] = ???
  override def updateUser(userId: UUID, userInfo: UserState): Future[Unit] = ???
  override def getUser(userId: UUID): Future[Option[UserState]] = ???
  override def getAllUsers(): Future[Seq[(UUID, UserInfo)]] = ???
  override def deleteUser(userId: UUID): Future[Unit] = ???
  override def updateArtifactReleaseDate(reference: Artifact.Reference, releaseDate: Instant): Future[Int] = ???

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    Future.successful(
      allProjects.update(ref, allProjects(ref).copy(githubInfo = Some(githubInfo), githubStatus = githubStatus))
    )

  override def getProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): Future[Seq[ProjectDependency]] =
    Future.successful(Seq.empty)
  override def getProjectDependents(ref: Project.Reference): Future[Seq[ProjectDependency]] =
    Future.successful(Seq.empty)
  override def countVersions(ref: Project.Reference): Future[Long] =
    Future.successful(getProjectArtifactsSync(ref).map(_.version).distinct.size)

  override def updateGithubStatus(ref: Project.Reference, status: GithubStatus): Future[Unit] =
    Future.successful(
      allProjects.update(ref, allProjects(ref).copy(githubStatus = status))
    )

  override def moveProject(ref: Project.Reference, info: GithubInfo, status: GithubStatus.Moved): Future[Unit] =
    val projectToMove = allProjects(ref)
    val newProject = projectToMove.copy(
      organization = status.destination.organization,
      repository = status.destination.repository
    )
    allProjects.update(status.destination, newProject)
    allProjects.update(ref, projectToMove.copy(githubStatus = status))
    Future.successful(())

  override def getProjectLatestArtifacts(ref: Project.Reference): Future[Seq[Artifact]] =
    val res = getProjectArtifactsSync(ref)
      .flatMap(a => latestArtifacts.get((a.groupId, a.artifactId)))
      .distinct
      .map(allArtifacts.apply)
    Future.successful(res)

  override def getArtifactIds(ref: Project.Reference): Future[Seq[(Artifact.GroupId, Artifact.ArtifactId)]] =
    Future.successful(getProjectArtifactsSync(ref).map(a => (a.groupId, a.artifactId)).distinct.toSeq)

  override def updateLatestVersion(ref: Project.Reference, artifact: Artifact.Reference): Future[Unit] =
    latestArtifacts += (artifact.groupId, artifact.artifactId) -> artifact
    Future.unit

  override def getArtifactVersions(
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      stableOnly: Boolean
  ): Future[Seq[Version]] =
    val res = allArtifacts.keys.collect {
      case Artifact.Reference(g, a, version) if g == groupId && a == artifactId => version
    }.toSeq
    Future.successful(res)

  override def getLatestArtifact(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Option[Artifact]] =
    Future.successful(latestArtifacts.get((groupId, artifactId)).map(allArtifacts.apply))
end InMemoryDatabase
