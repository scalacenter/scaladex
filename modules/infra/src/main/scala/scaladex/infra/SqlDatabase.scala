package scaladex.infra

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.HikariDataSource
import doobie.implicits._
import scaladex.core.model._
import scaladex.core.service.SchedulerDatabase
import scaladex.infra.sql._
import com.github.blemale.scaffeine.Scaffeine
import scala.concurrent.duration._

class SqlDatabase(datasource: HikariDataSource, xa: doobie.Transactor[IO]) extends SchedulerDatabase with LazyLogging {
  private val flyway = DoobieUtils.flyway(datasource)
  def migrate: IO[Unit] = IO(flyway.migrate())
  def dropTables: IO[Unit] = IO(flyway.clean())

  override def insertArtifact(artifact: Artifact): Future[Boolean] =
    run(ArtifactTable.insertIfNotExist.run(artifact)).map(_ >= 1)

  override def getArtifactVersions(
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      stableOnly: Boolean
  ): Future[Seq[Version]] =
    run(ArtifactTable.selectVersionByGroupIdAndArtifactId(stableOnly).to[Seq]((groupId, artifactId)))

  override def getLatestArtifact(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Option[Artifact]] =
    run(ArtifactTable.selectLatestArtifact.option((groupId, artifactId)))

  override def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] =
    run(ArtifactTable.insertIfNotExist.updateMany(artifacts)).map(_ => ())

  override def updateArtifacts(artifacts: Seq[Artifact.Reference], newRef: Project.Reference): Future[Int] = {
    val references = artifacts.map(newRef -> _)
    run(ArtifactTable.updateProjectRef.updateMany(references))
  }

  override def updateArtifactReleaseDate(ref: Artifact.Reference, releaseDate: Instant): Future[Int] =
    run(ArtifactTable.updateReleaseDate.run((releaseDate, ref)))

  override def getArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByGroupIdAndArtifactId.to[Seq](groupId, artifactId))

  override def getArtifact(ref: Artifact.Reference): Future[Option[Artifact]] =
    run(ArtifactTable.selectByReference.option(ref))

  override def getAllArtifacts(language: Option[Language], platform: Option[Platform]): Future[Seq[Artifact]] =
    run(ArtifactTable.selectAllArtifacts(language, platform).to[Seq])

  override def insertProject(project: Project): Future[Unit] =
    for {
      updated <- insertProjectRef(project.reference, project.githubStatus)
      _ <-
        if (updated) {
          project.githubInfo
            .map(updateGithubInfoAndStatus(project.reference, _, project.githubStatus))
            .getOrElse(Future.successful(()))
            .flatMap(_ => updateProjectSettings(project.reference, project.settings))
        } else {
          logger.warn(s"${project.reference} already inserted")
          Future.successful(())
        }
    } yield ()

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit] =
    run(ArtifactDependencyTable.insertIfNotExist.updateMany(dependencies)).map(_ => ())

  // return true if inserted, false if it already existed
  override def insertProjectRef(ref: Project.Reference, status: GithubStatus): Future[Boolean] =
    run(ProjectTable.insertIfNotExists.run((ref, status))).map(x => x >= 1)

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] =
    run(ProjectTable.selectReferenceAndStatus.to[Seq]).map(_.toMap)

  override def getAllProjects(): Future[Seq[Project]] =
    run(ProjectTable.selectProject.to[Seq])

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    for {
      _ <- updateGithubStatus(ref, githubStatus)
      _ <- run(GithubInfoTable.insertOrUpdate.run((ref, githubInfo, githubInfo)))
    } yield ()

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] =
    run(ProjectSettingsTable.insertOrUpdate.run((ref, settings, settings))).map(_ => ())

  override def getProject(ref: Project.Reference): Future[Option[Project]] =
    run(ProjectTable.selectByReference.option(ref))

  override def getProjectArtifactRefs(ref: Project.Reference, stableOnly: Boolean): Future[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectArtifactRefByProject(stableOnly).to[Seq](ref))

  override def getProjectArtifactRefs(ref: Project.Reference, name: Artifact.Name): Future[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectArtifactRefByProjectAndName.to[Seq]((ref, name)))

  override def getProjectArtifactRefs(
      ref: Project.Reference,
      version: Version
  ): Future[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectArtifactRefByProjectAndVersion.to[Seq]((ref, version)))

  override def getAllProjectArtifacts(ref: Project.Reference): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProject.to[Seq](ref))

  override def getProjectArtifacts(
      ref: Project.Reference,
      name: Artifact.Name,
      stableOnly: Boolean
  ): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProjectAndName(stableOnly).to[Seq](ref, name))

  override def getProjectArtifacts(
      ref: Project.Reference,
      name: Artifact.Name,
      version: Version
  ): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProjectAndNameAndVersion.to[Seq](ref, name, version))

  override def getProjectLatestArtifacts(ref: Project.Reference): Future[Seq[Artifact]] =
    run(ArtifactTable.selectLatestArtifacts.to[Seq](ref))

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

  private val countArtifactsCache = Scaffeine()
    .refreshAfterWrite(5.minutes)
    .buildAsyncFuture[Unit, Long](_ => run(ArtifactTable.count.unique))
  override def countArtifacts(): Future[Long] = countArtifactsCache.get(())

  def countDependencies(): Future[Long] =
    run(ArtifactDependencyTable.count.unique)

  override def getDirectDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Direct]] =
    run(ArtifactDependencyTable.selectDirectDependency.to[Seq](artifact.reference))

  override def getReverseDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Reverse]] =
    run(ArtifactDependencyTable.selectReverseDependency.to[Seq](artifact.reference))

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
    if (projectDependencies.isEmpty) Future.successful(0)
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
    run(ProjectTable.updateCreationDate.run((creationDate, ref))).map(_ => ())

  override def getGroupIds(): Future[Seq[Artifact.GroupId]] =
    run(ArtifactTable.selectGroupIds.to[Seq])

  override def getArtifactIds(ref: Project.Reference): Future[Seq[(Artifact.GroupId, Artifact.ArtifactId)]] =
    run(ArtifactTable.selectArtifactIds.to[Seq](ref))

  override def getArtifactRefs(): Future[Seq[Artifact.Reference]] =
    run(ArtifactTable.selectReferences.to[Seq])

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

  override def updateLatestVersion(ref: Artifact.Reference): Future[Unit] = {
    val transaction = for {
      _ <- ArtifactTable.setLatestVersion.run(ref)
      _ <- ArtifactTable.unsetOthersLatestVersion.run(ref)
    } yield ()
    run(transaction)
  }

  override def countVersions(ref: Project.Reference): Future[Long] =
    run(ArtifactTable.countVersionsByProject.unique(ref))

  override def moveProject(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      status: GithubStatus.Moved
  ): Future[Unit] =
    for {
      oldProject <- getProject(ref)
      _ <- updateGithubStatus(ref, status)
      _ <- run(ProjectTable.insertIfNotExists.run((status.destination, GithubStatus.Ok(status.updateDate))))
      _ <- updateProjectSettings(status.destination, oldProject.map(_.settings).getOrElse(Project.Settings.empty))
      _ <- run(GithubInfoTable.insertOrUpdate.run(status.destination, githubInfo, githubInfo))
    } yield ()

  def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): Future[Unit] =
    run(ProjectTable.updateGithubStatus.run(githubStatus, ref)).map(_ => ())

  private def run[A](v: doobie.ConnectionIO[A]): Future[A] =
    v.transact(xa).unsafeToFuture()
}
