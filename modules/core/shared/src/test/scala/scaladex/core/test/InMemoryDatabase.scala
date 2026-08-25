package scaladex.core.test

import java.time.Instant
import java.util.UUID

import scala.collection.mutable

import scaladex.core.model.*
import scaladex.core.service.SchedulerDatabase

import cats.effect.IO

class InMemoryDatabase extends SchedulerDatabase:

  private val allProjects = mutable.Map[Project.Reference, Project]()
  private val allArtifacts = mutable.Map[Artifact.Reference, Artifact]()
  private val allDependencies = mutable.Buffer[ArtifactDependency]()
  private val latestArtifacts =
    mutable.Map[(Project.Reference, Artifact.GroupId, Artifact.ArtifactId), Artifact.Reference]()

  def reset(): Unit =
    allProjects.clear()
    allArtifacts.clear()
    allDependencies.clear()

  override def insertArtifact(artifact: Artifact): IO[Boolean] =
    val isNewArtifact = !allArtifacts.contains(artifact.reference)
    allArtifacts += artifact.reference -> artifact
    IO.pure(isNewArtifact)

  override def insertProjectRef(ref: Project.Reference, status: GithubStatus): IO[Boolean] =
    val isNewProject = !allProjects.contains(ref)
    if isNewProject then allProjects.addOne(ref -> Project.default(ref, status))
    IO.pure(isNewProject)

  override def insertProject(project: Project): IO[Unit] =
    allProjects += project.reference -> project
    IO.unit

  override def insertArtifacts(artifacts: Seq[Artifact]): IO[Unit] =
    artifacts.foreach(a => allArtifacts += a.reference -> a)
    IO.unit

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): IO[Unit] =
    allDependencies ++= dependencies
    IO.unit

  override def deleteProjectDependencies(ref: Project.Reference): IO[Int] = ???

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): IO[Unit] =
    allProjects.update(ref, allProjects(ref).copy(settings = settings))
    IO.unit

  override def getProject(projectRef: Project.Reference): IO[Option[Project]] =
    IO.pure(allProjects.get(projectRef))

  override def getArtifacts(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): IO[Seq[Artifact]] =
    val res =
      allArtifacts.values.filter(a => a.projectRef == ref && a.groupId == groupId && a.artifactId == artifactId).toSeq
    IO.pure(res)

  override def getAllProjectArtifacts(ref: Project.Reference): IO[Seq[Artifact]] =
    IO.pure(getProjectArtifactsSync(ref))

  private def getProjectArtifactsSync(ref: Project.Reference): Seq[Artifact] =
    allArtifacts.values.filter(_.projectRef == ref).toSeq

  override def getProjectArtifactRefs(
      ref: Project.Reference,
      stableOnly: Boolean
  ): IO[Seq[Artifact.Reference]] =
    IO.pure(getProjectArtifactsSync(ref).map(_.reference))

  override def getProjectArtifactRefs(ref: Project.Reference, name: Artifact.Name): IO[Seq[Artifact.Reference]] =
    IO.pure(getProjectArtifactsSync(ref).map(_.reference).filter(_.name == name))

  override def getProjectArtifactRefs(
      ref: Project.Reference,
      version: Version
  ): IO[Seq[Artifact.Reference]] =
    IO.pure(getProjectArtifactsSync(ref).map(_.reference).filter(_.version == version))

  override def getProjectDependencies(projectRef: Project.Reference): IO[Seq[ArtifactDependency]] = ???

  override def getFormerReferences(projectRef: Project.Reference): IO[Seq[Project.Reference]] =
    val result = allProjects.view
      .mapValues(_.githubStatus)
      .collect { case (ref, GithubStatus.Moved(_, `projectRef`)) => ref }
      .toSeq
    IO.pure(result)

  override def getProjectArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      stableOnly: Boolean
  ): IO[Seq[Artifact]] =
    val res = getProjectArtifactsSync(ref).filter(a => a.name == artifactName && (!stableOnly || a.version.isStable))
    IO.pure(res)

  override def getProjectArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      version: Version
  ): IO[Seq[Artifact]] =
    IO.pure(
      getProjectArtifactsSync(ref).filter(a => a.name == artifactName && a.version == version)
    )

  override def getArtifact(ref: Artifact.Reference): IO[Option[Artifact]] =
    IO.pure(allArtifacts.get(ref))

  override def getAllArtifacts(
      maybeLanguage: Option[Language],
      maybePlatform: Option[Platform]
  ): IO[Seq[Artifact]] =
    val constraint = (maybeLanguage, maybePlatform) match
      case (Some(language), Some(platform)) =>
        (artifact: Artifact) => artifact.language == language && artifact.platform == platform
      case (Some(language), _) => (artifact: Artifact) => artifact.language == language
      case (_, Some(platform)) => (artifact: Artifact) => artifact.platform == platform
      case _ => (_: Artifact) => true
    IO.pure(allArtifacts.values.toSeq.filter(constraint))
  end getAllArtifacts

  override def getDirectDependencies(artifact: Artifact): IO[List[ArtifactDependency.Direct]] =
    IO.pure(Nil)

  override def getReverseDependencies(
      artifact: Artifact,
      limit: Int,
      offset: Int
  ): IO[List[ArtifactDependency.Reverse]] =
    IO.pure(Nil)

  override def countReverseDependencies(artifact: Artifact): IO[Long] =
    IO.pure(0L)

  override def countArtifacts(): IO[Long] =
    IO.pure(allArtifacts.size)

  override def getAllProjectsStatuses(): IO[Map[Project.Reference, GithubStatus]] =
    IO.pure(allProjects.view.mapValues(p => p.githubStatus).toMap)

  override def getAllProjects(): IO[Seq[Project]] =
    IO.pure(allProjects.values.toSeq)

  override def computeProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): IO[Seq[ProjectDependency]] = ???

  override def computeProjectsCreationDates(): IO[Seq[(Instant, Project.Reference)]] = ???

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): IO[Unit] =
    IO(allProjects.update(ref, allProjects(ref).copy(creationDate = Some(creationDate))))

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): IO[Int] = ???

  override def countProjectDependents(ref: Project.Reference): IO[Long] =
    IO.pure(0)

  override def updateArtifacts(allArtifacts: Seq[Artifact.Reference], newRef: Project.Reference): IO[Int] = ???
  override def getGroupIds(): IO[Seq[Artifact.GroupId]] = ???
  override def getGroupIds(limit: Int, offset: Int): IO[Seq[Artifact.GroupId]] = ???
  override def getArtifactRefs(): IO[Seq[Artifact.Reference]] = ???
  override def getArtifactRefs(groupId: Artifact.GroupId): IO[Seq[Artifact.Reference]] = ???
  override def getArtifactRefs(groupId: Artifact.GroupId, limit: Int, offset: Int): IO[Seq[Artifact.Reference]] =
    ???
  override def insertUser(userId: UUID, userInfo: UserInfo): IO[Unit] = ???
  override def updateUser(userId: UUID, userInfo: UserState): IO[Unit] = ???
  override def getUser(userId: UUID): IO[Option[UserState]] = ???
  override def getAllUsers(): IO[Seq[(UUID, UserInfo)]] = ???
  override def deleteUser(userId: UUID): IO[Unit] = ???
  override def updateArtifactReleaseDate(reference: Artifact.Reference, releaseDate: Instant): IO[Int] = ???

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): IO[Unit] =
    IO(
      allProjects.update(ref, allProjects(ref).copy(githubInfo = Some(githubInfo), githubStatus = githubStatus))
    )

  override def getProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): IO[Seq[ProjectDependency]] =
    IO.pure(Seq.empty)
  override def getProjectDependents(ref: Project.Reference): IO[Seq[ProjectDependency]] =
    IO.pure(Seq.empty)
  override def countVersions(ref: Project.Reference): IO[Long] =
    IO.pure(getProjectArtifactsSync(ref).map(_.version).distinct.size)

  override def updateGithubStatus(ref: Project.Reference, status: GithubStatus): IO[Unit] =
    IO(
      allProjects.update(ref, allProjects(ref).copy(githubStatus = status))
    )

  override def moveProject(ref: Project.Reference, info: GithubInfo, status: GithubStatus.Moved): IO[Unit] =
    val projectToMove = allProjects(ref)
    val newProject = projectToMove.copy(
      organization = status.destination.organization,
      repository = status.destination.repository
    )
    allProjects.update(status.destination, newProject)
    allProjects.update(ref, projectToMove.copy(githubStatus = status))
    IO.unit

  override def getProjectLatestArtifacts(ref: Project.Reference): IO[Seq[Artifact]] =
    val res = getProjectArtifactsSync(ref)
      .flatMap(a => latestArtifacts.get((ref, a.groupId, a.artifactId)))
      .distinct
      .map(allArtifacts.apply)
    IO.pure(res)

  override def getArtifactIds(ref: Project.Reference): IO[Seq[(Artifact.GroupId, Artifact.ArtifactId)]] =
    IO.pure(getProjectArtifactsSync(ref).map(a => (a.groupId, a.artifactId)).distinct.toSeq)

  override def updateLatestVersion(ref: Project.Reference, artifact: Artifact.Reference): IO[Unit] =
    latestArtifacts += (ref, artifact.groupId, artifact.artifactId) -> artifact
    IO.unit

  override def getArtifactVersions(
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      stableOnly: Boolean
  ): IO[Seq[Version]] =
    val res = allArtifacts.keys.collect {
      case Artifact.Reference(g, a, version) if g == groupId && a == artifactId => version
    }.toSeq
    IO.pure(res)

  override def getLatestArtifact(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): IO[Option[Artifact]] =
    IO.pure(latestArtifacts.get((ref, groupId, artifactId)).map(allArtifacts.apply))

  override def getLatestArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): IO[Seq[Artifact]] =
    val res = latestArtifacts.values
      .filter(a => a.groupId == groupId && a.artifactId == artifactId)
      .map(allArtifacts.apply)
      .toSeq
    IO.pure(res)
end InMemoryDatabase
