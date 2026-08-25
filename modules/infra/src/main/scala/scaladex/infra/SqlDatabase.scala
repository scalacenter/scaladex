package scaladex.infra

import java.time.Instant
import java.util.UUID
import java.util.concurrent.Semaphore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

import scaladex.core.model.*
import scaladex.core.service.SchedulerDatabase
import scaladex.infra.config.CacheConfig
import scaladex.infra.sql.*

import cats.effect.IO
import com.github.blemale.scaffeine.AsyncLoadingCache
import com.github.blemale.scaffeine.Scaffeine
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits.*

class SqlDatabase(
    xa: doobie.Transactor[IO],
    cacheConfig: CacheConfig,
    maxConcurrentQueries: Option[Int] = None
) extends SchedulerDatabase
    with LazyLogging:

  private val queryPermits: Option[Semaphore] = maxConcurrentQueries.map(new Semaphore(_))

  private def buildCache[K, V](loader: K => Future[V]): AsyncLoadingCache[K, V] =
    Scaffeine()
      .refreshAfterWrite(cacheConfig.refreshAfter)
      .expireAfterWrite(cacheConfig.expireAfter)
      .maximumSize(cacheConfig.maxSize)
      .buildAsyncFuture(loader)

  private val projectCache: AsyncLoadingCache[Project.Reference, Option[Project]] =
    buildCache(ref => run(ProjectTable.selectByReference.option(ref)))

  private val projectArtifactsByNameCache
      : AsyncLoadingCache[(Project.Reference, Artifact.Name, Boolean), Seq[Artifact]] =
    buildCache {
      case (ref, name, stableOnly) =>
        run(ArtifactTable.selectArtifactByProjectAndName(stableOnly).to[Seq](ref, name))
    }

  private val projectArtifactsByVersionCache
      : AsyncLoadingCache[(Project.Reference, Artifact.Name, Version), Seq[Artifact]] =
    buildCache {
      case (ref, name, version) =>
        run(ArtifactTable.selectArtifactByProjectAndNameAndVersion.to[Seq](ref, name, version))
    }

  private val projectArtifactRefsCache: AsyncLoadingCache[(Project.Reference, Boolean), Seq[Artifact.Reference]] =
    buildCache {
      case (ref, stableOnly) =>
        run(ArtifactTable.selectArtifactRefByProject(stableOnly).to[Seq](ref))
    }

  private val projectArtifactRefsByNameCache
      : AsyncLoadingCache[(Project.Reference, Artifact.Name), Seq[Artifact.Reference]] =
    buildCache {
      case (ref, name) =>
        run(ArtifactTable.selectArtifactRefByProjectAndName.to[Seq]((ref, name)))
    }

  private val projectArtifactRefsByVersionCache
      : AsyncLoadingCache[(Project.Reference, Version), Seq[Artifact.Reference]] =
    buildCache {
      case (ref, version) =>
        run(ArtifactTable.selectArtifactRefByProjectAndVersion.to[Seq]((ref, version)))
    }

  private val projectLatestArtifactsCache: AsyncLoadingCache[Project.Reference, Seq[Artifact]] =
    buildCache(ref => run(ArtifactTable.selectProjectLatestArtifacts.to[Seq](ref)))

  private val countArtifactsCache: AsyncLoadingCache[Unit, Long] =
    Scaffeine().refreshAfterWrite(5.minutes).buildAsyncFuture[Unit, Long](_ => run(ArtifactTable.count.unique))

  private val directDependenciesCache: AsyncLoadingCache[Artifact.Reference, Seq[ArtifactDependency.Direct]] =
    buildCache(ref => run(ArtifactDependencyTable.selectDirectDependency.to[Seq](ref)))

  private val reverseDependenciesCache
      : AsyncLoadingCache[(Artifact.Reference, Int, Int), Seq[ArtifactDependency.Reverse]] =
    buildCache {
      case (ref, limit, offset) =>
        run(ArtifactDependencyTable.selectReverseDependencyPage.to[Seq]((ref, limit.toLong, offset.toLong)))
          .map(_.sorted)
    }

  private val reverseDependencyCountCache: AsyncLoadingCache[Artifact.Reference, Long] =
    buildCache(ref => run(ArtifactDependencyTable.countReverseDependency.unique(ref)))

  override def insertArtifact(artifact: Artifact): Future[Boolean] =
    run(ArtifactTable.insertIfNotExist.run(artifact)).map { inserted =>
      invalidateArtifactRefs(artifact)
      inserted >= 1
    }

  override def getArtifactVersions(
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      stableOnly: Boolean
  ): Future[Seq[Version]] =
    run(ArtifactTable.selectVersionByGroupIdAndArtifactId(stableOnly).to[Seq]((groupId, artifactId)))

  override def getLatestArtifact(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): Future[Option[Artifact]] =
    run(ArtifactTable.selectLatestArtifact.option((ref, groupId, artifactId)))

  override def getLatestArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Artifact]] =
    run(ArtifactTable.selectLatestArtifacts.to[Seq]((groupId, artifactId)))

  override def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] =
    run(ArtifactTable.insertIfNotExist.updateMany(artifacts))
      .map(_ => artifacts.foreach(invalidateArtifactRefs))

  override def updateArtifacts(artifacts: Seq[Artifact.Reference], newRef: Project.Reference): Future[Int] =
    val references = artifacts.map(newRef -> _)
    run(ArtifactTable.updateProjectRef.updateMany(references)).map { updated =>
      invalidateAllArtifactRefs()
      updated
    }

  override def updateArtifactReleaseDate(ref: Artifact.Reference, releaseDate: Instant): Future[Int] =
    run(ArtifactTable.updateReleaseDate.run((releaseDate, ref)))

  override def getArtifacts(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByGroupIdAndArtifactId.to[Seq]((ref, groupId, artifactId)))

  override def getArtifact(ref: Artifact.Reference): Future[Option[Artifact]] =
    run(ArtifactTable.selectByReference.option(ref))

  override def getAllArtifacts(language: Option[Language], platform: Option[Platform]): Future[Seq[Artifact]] =
    run(ArtifactTable.selectAllArtifacts(language, platform).to[Seq])

  override def insertProject(project: Project): Future[Unit] =
    for
      updated <- insertProjectRef(project.reference, project.githubStatus)
      _ <-
        if updated then
          project.githubInfo
            .map(updateGithubInfoAndStatus(project.reference, _, project.githubStatus))
            .getOrElse(Future.successful(()))
            .flatMap(_ => updateProjectSettings(project.reference, project.settings))
        else
          logger.warn(s"${project.reference} already inserted")
          Future.successful(())
    yield ()

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit] =
    run(ArtifactDependencyTable.insertIfNotExist.updateMany(dependencies)).map(_ => ())

  // return true if inserted, false if it already existed
  override def insertProjectRef(ref: Project.Reference, status: GithubStatus): Future[Boolean] =
    run(ProjectTable.insertIfNotExists.run((ref, status))).map { inserted =>
      invalidateProject(ref)
      inserted >= 1
    }

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] =
    run(ProjectTable.selectReferenceAndStatus.to[Seq]).map(_.toMap)

  override def getAllProjects(): Future[Seq[Project]] =
    run(ProjectTable.selectProject.to[Seq])

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    for
      _ <- updateGithubStatus(ref, githubStatus)
      _ <- run(GithubInfoTable.insertOrUpdate.run((ref, githubInfo, githubInfo)))
    yield invalidateProject(ref)

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] =
    run(ProjectSettingsTable.insertOrUpdate.run((ref, settings, settings))).map(_ => invalidateProject(ref))

  override def getProject(ref: Project.Reference): Future[Option[Project]] =
    projectCache.get(ref)
  private def invalidateProject(ref: Project.Reference): Unit =
    projectCache.underlying.synchronous().invalidate(ref)

  override def getProjectArtifactRefs(ref: Project.Reference, stableOnly: Boolean): Future[Seq[Artifact.Reference]] =
    projectArtifactRefsCache.get((ref, stableOnly))

  override def getProjectArtifactRefs(ref: Project.Reference, name: Artifact.Name): Future[Seq[Artifact.Reference]] =
    projectArtifactRefsByNameCache.get((ref, name))

  override def getProjectArtifactRefs(
      ref: Project.Reference,
      version: Version
  ): Future[Seq[Artifact.Reference]] =
    projectArtifactRefsByVersionCache.get((ref, version))

  private def invalidateArtifactRefs(artifact: Artifact): Unit =
    val ref = artifact.projectRef
    val byProject = projectArtifactRefsCache.underlying.synchronous()
    byProject.invalidate((ref, true))
    byProject.invalidate((ref, false))
    projectArtifactRefsByNameCache.underlying.synchronous().invalidate((ref, artifact.name))
    projectArtifactRefsByVersionCache.underlying.synchronous().invalidate((ref, artifact.version))

  private def invalidateAllArtifactRefs(): Unit =
    projectArtifactRefsCache.underlying.synchronous().invalidateAll()
    projectArtifactRefsByNameCache.underlying.synchronous().invalidateAll()
    projectArtifactRefsByVersionCache.underlying.synchronous().invalidateAll()

  override def getAllProjectArtifacts(ref: Project.Reference): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProject.to[Seq](ref))

  override def getProjectArtifacts(
      ref: Project.Reference,
      name: Artifact.Name,
      stableOnly: Boolean
  ): Future[Seq[Artifact]] =
    projectArtifactsByNameCache.get((ref, name, stableOnly))

  override def getProjectArtifacts(
      ref: Project.Reference,
      name: Artifact.Name,
      version: Version
  ): Future[Seq[Artifact]] =
    projectArtifactsByVersionCache.get((ref, name, version))

  override def getProjectLatestArtifacts(ref: Project.Reference): Future[Seq[Artifact]] =
    projectLatestArtifactsCache.get(ref)

  override def getProjectDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]] =
    run(ArtifactDependencyTable.selectDependencyFromProject.to[Seq](projectRef))

  override def getProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): Future[Seq[ProjectDependency]] =
    run(ProjectDependenciesTable.getDependencies.to[Seq]((ref, version)))

  override def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]] =
    run(ProjectTable.selectByNewReference.to[Seq](projectRef))

  def countProjects(): Future[Long] =
    run(ProjectTable.countProjects.unique)

  override def countArtifacts(): Future[Long] = countArtifactsCache.get(())

  def countDependencies(): Future[Long] =
    run(ArtifactDependencyTable.count.unique)

  override def getDirectDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Direct]] =
    directDependenciesCache.get(artifact.reference)

  override def getReverseDependencies(
      artifact: Artifact,
      limit: Int,
      offset: Int
  ): Future[Seq[ArtifactDependency.Reverse]] =
    reverseDependenciesCache.get((artifact.reference, limit, offset))

  override def countReverseDependencies(artifact: Artifact): Future[Long] =
    reverseDependencyCountCache.get(artifact.reference)

  def countGithubInfo(): Future[Long] =
    run(GithubInfoTable.count.unique)

  def countProjectSettings(): Future[Long] =
    run(ProjectSettingsTable.count.unique)

  override def computeProjectDependencies(
      ref: Project.Reference,
      version: Version
  ): Future[Seq[ProjectDependency]] =
    run(ArtifactDependencyTable.computeProjectDependencies.to[Seq]((ref, version)))

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] =
    if projectDependencies.isEmpty then Future.successful(0)
    else run(ProjectDependenciesTable.insertOrUpdate.updateMany(projectDependencies))

  override def deleteProjectDependencies(ref: Project.Reference): Future[Int] =
    run(ProjectDependenciesTable.deleteBySource.run(ref))

  override def countProjectDependents(projectRef: Project.Reference): Future[Long] =
    run(ProjectDependenciesTable.countDependents.unique(projectRef))

  override def getProjectDependents(ref: Project.Reference): Future[Seq[ProjectDependency]] =
    run(ProjectDependenciesTable.getDependents.to[Seq](ref))

  override def computeProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] =
    run(ArtifactTable.selectOldestByProject.to[Seq])

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    run(ProjectTable.updateCreationDate.run((creationDate, ref))).map(_ => invalidateProject(ref))

  override def getGroupIds(): Future[Seq[Artifact.GroupId]] =
    run(ArtifactTable.selectGroupIds.to[Seq])

  override def getGroupIds(limit: Int, offset: Int): Future[Seq[Artifact.GroupId]] =
    run(ArtifactTable.selectGroupIdsPage.to[Seq]((limit.toLong, offset.toLong)))

  override def getArtifactIds(ref: Project.Reference): Future[Seq[(Artifact.GroupId, Artifact.ArtifactId)]] =
    run(ArtifactTable.selectArtifactIds.to[Seq](ref))

  override def getArtifactRefs(): Future[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectReferences.to[Seq])

  override def getArtifactRefs(groupId: Artifact.GroupId): Future[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectReferencesByGroupId.to[Seq](groupId))

  override def getArtifactRefs(groupId: Artifact.GroupId, limit: Int, offset: Int): Future[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectReferencesByGroupIdPage.to[Seq]((groupId, limit.toLong, offset.toLong)))

  override def insertUser(userId: UUID, userInfo: UserInfo): Future[Unit] =
    run(UserSessionsTable.insert.run((userId, userInfo)).map(_ => ()))

  override def updateUser(userId: UUID, userState: UserState): Future[Unit] =
    run(UserSessionsTable.update.run((userState, userId)).map(_ => ()))

  override def getUser(userId: UUID): Future[Option[UserState]] =
    run(UserSessionsTable.selectById.option(userId))

  override def getAllUsers(): Future[Seq[(UUID, UserInfo)]] =
    run(UserSessionsTable.selectAll.to[Seq])

  override def deleteUser(userId: UUID): Future[Unit] =
    run(UserSessionsTable.deleteById.run(userId).map(_ => ()))

  override def updateLatestVersion(ref: Project.Reference, artifact: Artifact.Reference): Future[Unit] =
    val transaction = for
      _ <- ArtifactTable.setLatestVersion.run((ref, artifact))
      _ <- ArtifactTable.unsetOthersLatestVersion.run((ref, artifact))
    yield ()
    run(transaction).map(_ => projectLatestArtifactsCache.underlying.synchronous().invalidate(ref))

  override def countVersions(ref: Project.Reference): Future[Long] =
    run(ArtifactTable.countVersionsByProject.unique(ref))

  override def moveProject(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      status: GithubStatus.Moved
  ): Future[Unit] =
    for
      oldProject <- getProject(ref)
      _ <- updateGithubStatus(ref, status)
      _ <- run(ProjectTable.insertIfNotExists.run((status.destination, GithubStatus.Ok(status.updateDate))))
      _ <- updateProjectSettings(status.destination, oldProject.map(_.settings).getOrElse(Project.Settings.empty))
      _ <- run(GithubInfoTable.insertOrUpdate.run(status.destination, githubInfo, githubInfo))
    yield
      invalidateProject(ref)
      invalidateProject(status.destination)

  def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): Future[Unit] =
    run(ProjectTable.updateGithubStatus.run(githubStatus, ref)).map(_ => invalidateProject(ref))

  private def run[A](v: doobie.ConnectionIO[A]): Future[A] =
    queryPermits match
      case None => v.transact(xa).unsafeToFuture()
      case Some(permits) =>
        if !permits.tryAcquire() then Future.failed(DatabaseOverloadedException)
        else
          val future = v.transact(xa).unsafeToFuture()
          future.onComplete(_ => permits.release())
          future
end SqlDatabase

object DatabaseOverloadedException extends RuntimeException("database overloaded")
