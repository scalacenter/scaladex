package scaladex.infra

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import scaladex.core.model.*
import scaladex.core.service.SchedulerDatabase
import scaladex.infra.config.CacheConfig
import scaladex.infra.sql.*

import cats.effect.IO
import com.github.blemale.scaffeine.Cache
import com.github.blemale.scaffeine.Scaffeine
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits.*

class SqlDatabase(xa: doobie.Transactor[IO], cacheConfig: CacheConfig) extends SchedulerDatabase with LazyLogging:

  // Sync values only: a miss runs `load` on the caller's IO so Doobie can cancel it.
  // refreshAfterWrite is omitted because Caffeine's refresh is Future-based and uncancellable.
  private def buildCache[K, V](): Cache[K, V] =
    Scaffeine()
      .expireAfterWrite(cacheConfig.expireAfter)
      .maximumSize(cacheConfig.maxSize)
      .build[K, V]()

  private def getCached[K, V](cache: Cache[K, V], key: K)(load: => IO[V]): IO[V] =
    IO(cache.getIfPresent(key)).flatMap {
      case Some(value) => IO.pure(value)
      case None =>
        load.map { value =>
          cache.put(key, value)
          value
        }
    }

  private val projectCache: Cache[Project.Reference, Option[Project]] = buildCache()

  private val projectArtifactsByNameCache: Cache[(Project.Reference, Artifact.Name, Boolean), Seq[Artifact]] =
    buildCache()

  private val projectArtifactsByVersionCache: Cache[(Project.Reference, Artifact.Name, Version), Seq[Artifact]] =
    buildCache()

  private val projectArtifactRefsCache: Cache[(Project.Reference, Boolean), Seq[Artifact.Reference]] =
    buildCache()

  private val projectArtifactRefsByNameCache: Cache[(Project.Reference, Artifact.Name), Seq[Artifact.Reference]] =
    buildCache()

  private val projectArtifactRefsByVersionCache: Cache[(Project.Reference, Version), Seq[Artifact.Reference]] =
    buildCache()

  private val projectLatestArtifactsCache: Cache[Project.Reference, Seq[Artifact]] = buildCache()

  private val countArtifactsCache: Cache[Unit, Long] =
    Scaffeine()
      .expireAfterWrite(5.minutes)
      .build[Unit, Long]()

  private val directDependenciesCache: Cache[Artifact.Reference, Seq[ArtifactDependency.Direct]] =
    buildCache()

  private val reverseDependenciesCache: Cache[(Artifact.Reference, Int, Int), Seq[ArtifactDependency.Reverse]] =
    buildCache()

  private val reverseDependencyCountCache: Cache[Artifact.Reference, Long] = buildCache()

  override def insertArtifact(artifact: Artifact): IO[Boolean] =
    run(ArtifactTable.insertIfNotExist.run(artifact)).map { inserted =>
      invalidateArtifactRefs(artifact)
      inserted >= 1
    }

  override def getArtifactVersions(
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      stableOnly: Boolean
  ): IO[Seq[Version]] =
    run(ArtifactTable.selectVersionByGroupIdAndArtifactId(stableOnly).to[Seq]((groupId, artifactId)))

  override def getLatestArtifact(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): IO[Option[Artifact]] =
    run(ArtifactTable.selectLatestArtifact.option((ref, groupId, artifactId)))

  override def getLatestArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): IO[Seq[Artifact]] =
    run(ArtifactTable.selectLatestArtifacts.to[Seq]((groupId, artifactId)))

  override def insertArtifacts(artifacts: Seq[Artifact]): IO[Unit] =
    run(ArtifactTable.insertIfNotExist.updateMany(artifacts))
      .map(_ => artifacts.foreach(invalidateArtifactRefs))
      .void

  override def updateArtifacts(artifacts: Seq[Artifact.Reference], newRef: Project.Reference): IO[Int] =
    val references = artifacts.map(newRef -> _)
    run(ArtifactTable.updateProjectRef.updateMany(references)).map { updated =>
      invalidateAllArtifactRefs()
      updated
    }

  override def updateArtifactReleaseDate(ref: Artifact.Reference, releaseDate: Instant): IO[Int] =
    run(ArtifactTable.updateReleaseDate.run((releaseDate, ref)))

  override def getArtifacts(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): IO[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByGroupIdAndArtifactId.to[Seq]((ref, groupId, artifactId)))

  override def getArtifact(ref: Artifact.Reference): IO[Option[Artifact]] =
    run(ArtifactTable.selectByReference.option(ref))

  override def getAllArtifacts(language: Option[Language], platform: Option[Platform]): IO[Seq[Artifact]] =
    run(ArtifactTable.selectAllArtifacts(language, platform).to[Seq])

  override def insertProject(project: Project): IO[Unit] =
    for
      updated <- insertProjectRef(project.reference, project.githubStatus)
      _ <-
        if updated then
          project.githubInfo
            .map(updateGithubInfoAndStatus(project.reference, _, project.githubStatus))
            .getOrElse(IO.unit)
            .flatMap(_ => updateProjectSettings(project.reference, project.settings))
        else
          logger.warn(s"${project.reference} already inserted")
          IO.unit
    yield ()

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): IO[Unit] =
    run(ArtifactDependencyTable.insertIfNotExist.updateMany(dependencies)).void

  // return true if inserted, false if it already existed
  override def insertProjectRef(ref: Project.Reference, status: GithubStatus): IO[Boolean] =
    run(ProjectTable.insertIfNotExists.run((ref, status))).map { inserted =>
      invalidateProject(ref)
      inserted >= 1
    }

  override def getAllProjectsStatuses(): IO[Map[Project.Reference, GithubStatus]] =
    run(ProjectTable.selectReferenceAndStatus.to[Seq]).map(_.toMap)

  override def getAllProjects(): IO[Seq[Project]] =
    run(ProjectTable.selectProject.to[Seq])

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): IO[Unit] =
    for
      _ <- updateGithubStatus(ref, githubStatus)
      _ <- run(GithubInfoTable.insertOrUpdate.run((ref, githubInfo, githubInfo)))
    yield invalidateProject(ref)

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): IO[Unit] =
    run(ProjectSettingsTable.insertOrUpdate.run((ref, settings, settings))).map(_ => invalidateProject(ref))

  override def getProject(ref: Project.Reference): IO[Option[Project]] =
    getCached(projectCache, ref)(run(ProjectTable.selectByReference.option(ref)))
  private def invalidateProject(ref: Project.Reference): Unit =
    projectCache.invalidate(ref)

  override def getProjectArtifactRefs(ref: Project.Reference, stableOnly: Boolean): IO[Seq[Artifact.Reference]] =
    getCached(projectArtifactRefsCache, (ref, stableOnly))(
      run(ArtifactTable.selectArtifactRefByProject(stableOnly).to[Seq](ref))
    )

  override def getProjectArtifactRefs(ref: Project.Reference, name: Artifact.Name): IO[Seq[Artifact.Reference]] =
    getCached(projectArtifactRefsByNameCache, (ref, name))(
      run(ArtifactTable.selectArtifactRefByProjectAndName.to[Seq]((ref, name)))
    )

  override def getProjectArtifactRefs(
      ref: Project.Reference,
      version: Version
  ): IO[Seq[Artifact.Reference]] =
    getCached(projectArtifactRefsByVersionCache, (ref, version))(
      run(ArtifactTable.selectArtifactRefByProjectAndVersion.to[Seq]((ref, version)))
    )

  private def invalidateArtifactRefs(artifact: Artifact): Unit =
    val ref = artifact.projectRef
    projectArtifactRefsCache.invalidate((ref, true))
    projectArtifactRefsCache.invalidate((ref, false))
    projectArtifactRefsByNameCache.invalidate((ref, artifact.name))
    projectArtifactRefsByVersionCache.invalidate((ref, artifact.version))

  private def invalidateAllArtifactRefs(): Unit =
    projectArtifactRefsCache.invalidateAll()
    projectArtifactRefsByNameCache.invalidateAll()
    projectArtifactRefsByVersionCache.invalidateAll()

  override def getAllProjectArtifacts(ref: Project.Reference): IO[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProject.to[Seq](ref))

  override def getProjectArtifacts(
      ref: Project.Reference,
      name: Artifact.Name,
      stableOnly: Boolean
  ): IO[Seq[Artifact]] =
    getCached(projectArtifactsByNameCache, (ref, name, stableOnly))(
      run(ArtifactTable.selectArtifactByProjectAndName(stableOnly).to[Seq](ref, name))
    )

  override def getProjectArtifacts(
      ref: Project.Reference,
      name: Artifact.Name,
      version: Version
  ): IO[Seq[Artifact]] =
    getCached(projectArtifactsByVersionCache, (ref, name, version))(
      run(ArtifactTable.selectArtifactByProjectAndNameAndVersion.to[Seq](ref, name, version))
    )

  override def getProjectLatestArtifacts(ref: Project.Reference): IO[Seq[Artifact]] =
    getCached(projectLatestArtifactsCache, ref)(run(ArtifactTable.selectProjectLatestArtifacts.to[Seq](ref)))

  override def getProjectDependencies(projectRef: Project.Reference): IO[Seq[ArtifactDependency]] =
    run(ArtifactDependencyTable.selectDependencyFromProject.to[Seq](projectRef))

  override def getProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): IO[Seq[ProjectDependency]] =
    run(ProjectDependenciesTable.getDependencies.to[Seq]((ref, version)))

  override def getFormerReferences(projectRef: Project.Reference): IO[Seq[Project.Reference]] =
    run(ProjectTable.selectByNewReference.to[Seq](projectRef))

  def countProjects(): IO[Long] =
    run(ProjectTable.countProjects.unique)

  override def countArtifacts(): IO[Long] =
    getCached(countArtifactsCache, ())(run(ArtifactTable.count.unique))

  def countDependencies(): IO[Long] =
    run(ArtifactDependencyTable.count.unique)

  override def getDirectDependencies(artifact: Artifact): IO[Seq[ArtifactDependency.Direct]] =
    getCached(directDependenciesCache, artifact.reference)(
      run(ArtifactDependencyTable.selectDirectDependency.to[Seq](artifact.reference))
    )

  override def getReverseDependencies(
      artifact: Artifact,
      limit: Int,
      offset: Int
  ): IO[Seq[ArtifactDependency.Reverse]] =
    getCached(reverseDependenciesCache, (artifact.reference, limit, offset))(
      run(
        ArtifactDependencyTable.selectReverseDependencyPage.to[Seq]((artifact.reference, limit.toLong, offset.toLong))
      )
        .map(_.sorted)
    )

  override def countReverseDependencies(artifact: Artifact): IO[Long] =
    getCached(reverseDependencyCountCache, artifact.reference)(
      run(ArtifactDependencyTable.countReverseDependency.unique(artifact.reference))
    )

  def countGithubInfo(): IO[Long] =
    run(GithubInfoTable.count.unique)

  def countProjectSettings(): IO[Long] =
    run(ProjectSettingsTable.count.unique)

  override def computeProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): IO[Seq[ProjectDependency]] =
    run(ArtifactDependencyTable.computeProjectDependencies.to[Seq]((ref, version)))

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): IO[Int] =
    if projectDependencies.isEmpty then IO.pure(0)
    else run(ProjectDependenciesTable.insertOrUpdate.updateMany(projectDependencies))

  override def deleteProjectDependencies(ref: Project.Reference): IO[Int] =
    run(ProjectDependenciesTable.deleteBySource.run(ref))

  override def countProjectDependents(projectRef: Project.Reference): IO[Long] =
    run(ProjectDependenciesTable.countDependents.unique(projectRef))

  override def getProjectDependents(ref: Project.Reference): IO[Seq[ProjectDependency]] =
    run(ProjectDependenciesTable.getDependents.to[Seq](ref))

  override def computeProjectsCreationDates(): IO[Seq[(Instant, Project.Reference)]] =
    run(ArtifactTable.selectOldestByProject.to[Seq])

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): IO[Unit] =
    run(ProjectTable.updateCreationDate.run((creationDate, ref))).map(_ => invalidateProject(ref))

  override def getGroupIds(): IO[Seq[Artifact.GroupId]] =
    run(ArtifactTable.selectGroupIds.to[Seq])

  override def getGroupIds(limit: Int, offset: Int): IO[Seq[Artifact.GroupId]] =
    run(ArtifactTable.selectGroupIdsPage.to[Seq]((limit.toLong, offset.toLong)))

  override def getArtifactIds(ref: Project.Reference): IO[Seq[(Artifact.GroupId, Artifact.ArtifactId)]] =
    run(ArtifactTable.selectArtifactIds.to[Seq](ref))

  override def getArtifactRefs(): IO[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectReferences.to[Seq])

  override def getArtifactRefs(groupId: Artifact.GroupId): IO[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectReferencesByGroupId.to[Seq](groupId))

  override def getArtifactRefs(groupId: Artifact.GroupId, limit: Int, offset: Int): IO[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectReferencesByGroupIdPage.to[Seq]((groupId, limit.toLong, offset.toLong)))

  override def insertUser(userId: UUID, userInfo: UserInfo): IO[Unit] =
    run(UserSessionsTable.insert.run((userId, userInfo)).map(_ => ()))

  override def updateUser(userId: UUID, userState: UserState): IO[Unit] =
    run(UserSessionsTable.update.run((userState, userId)).map(_ => ()))

  override def getUser(userId: UUID): IO[Option[UserState]] =
    run(UserSessionsTable.selectById.option(userId))

  override def getAllUsers(): IO[Seq[(UUID, UserInfo)]] =
    run(UserSessionsTable.selectAll.to[Seq])

  override def deleteUser(userId: UUID): IO[Unit] =
    run(UserSessionsTable.deleteById.run(userId).map(_ => ()))

  override def updateLatestVersion(ref: Project.Reference, artifact: Artifact.Reference): IO[Unit] =
    val transaction = for
      _ <- ArtifactTable.setLatestVersion.run((ref, artifact))
      _ <- ArtifactTable.unsetOthersLatestVersion.run((ref, artifact))
    yield ()
    run(transaction).map(_ => projectLatestArtifactsCache.invalidate(ref))

  override def countVersions(ref: Project.Reference): IO[Long] =
    run(ArtifactTable.countVersionsByProject.unique(ref))

  override def moveProject(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      status: GithubStatus.Moved
  ): IO[Unit] =
    for
      oldProject <- getProject(ref)
      _ <- updateGithubStatus(ref, status)
      _ <- run(ProjectTable.insertIfNotExists.run((status.destination, GithubStatus.Ok(status.updateDate))))
      _ <- updateProjectSettings(status.destination, oldProject.map(_.settings).getOrElse(Project.Settings.empty))
      _ <- run(GithubInfoTable.insertOrUpdate.run(status.destination, githubInfo, githubInfo))
    yield
      invalidateProject(ref)
      invalidateProject(status.destination)

  def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): IO[Unit] =
    run(ProjectTable.updateGithubStatus.run(githubStatus, ref)).map(_ => invalidateProject(ref))

  private def run[A](v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)
end SqlDatabase
