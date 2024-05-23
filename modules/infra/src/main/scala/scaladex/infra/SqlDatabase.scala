package scaladex.infra

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.HikariDataSource
import doobie.implicits._
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
import scaladex.infra.sql.ArtifactDependencyTable
import scaladex.infra.sql.ArtifactTable
import scaladex.infra.sql.DoobieUtils
import scaladex.infra.sql.GithubInfoTable
import scaladex.infra.sql.ProjectDependenciesTable
import scaladex.infra.sql.ProjectSettingsTable
import scaladex.infra.sql.ProjectTable
import scaladex.infra.sql.ReleaseDependenciesTable
import scaladex.infra.sql.ReleaseTable
import scaladex.infra.sql.UserSessionsTable

class SqlDatabase(datasource: HikariDataSource, xa: doobie.Transactor[IO]) extends SchedulerDatabase with LazyLogging {
  private val flyway = DoobieUtils.flyway(datasource)
  def migrate: IO[Unit] = IO(flyway.migrate())
  def dropTables: IO[Unit] = IO(flyway.clean())

  override def insertArtifact(
      artifact: Artifact,
      dependencies: Seq[ArtifactDependency],
      time: Instant
  ): Future[Boolean] = {
    val unknownStatus = GithubStatus.Unknown(time)
    for {
      isNewProject <- insertProjectRef(artifact.projectRef, unknownStatus)
      _ <- run(ArtifactTable.insertIfNotExist(artifact))
      _ <- run(ReleaseTable.insertIfNotExists.run(artifact.release))
      _ <- insertDependencies(dependencies)
    } yield isNewProject
  }

  override def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] =
    run(ArtifactTable.insertIfNotExist(artifacts)).map(_ => ())

  override def updateArtifacts(artifacts: Seq[Artifact], newRef: Project.Reference): Future[Int] = {
    val mavenReferences = artifacts.map(r => newRef -> r.mavenReference)
    run(ArtifactTable.updateProjectRef.updateMany(mavenReferences))
  }

  override def updateArtifactReleaseDate(reference: Artifact.MavenReference, releaseDate: Instant): Future[Int] =
    run(ArtifactTable.updateReleaseDate.run((releaseDate, reference)))

  override def getArtifacts(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByGroupIdAndArtifactId.to[Seq](groupId, artifactId))

  override def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProject.to[Seq](projectRef))

  override def getArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      version: SemanticVersion
  ): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactBy.to[Seq](ref, artifactName, version))

  override def getArtifactsByName(ref: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProjectAndName.to[Seq]((ref, artifactName)))

  override def getLatestArtifacts(ref: Project.Reference, preferStableVersions: Boolean): Future[Seq[Artifact]] = {
    val latestArtifactsF = run(ArtifactTable.selectLatestArtifacts(stableOnly = false).to[Seq](ref))
    if (preferStableVersions) {
      for {
        latestStableArtifacts <- run(ArtifactTable.selectLatestArtifacts(stableOnly = true).to[Seq](ref))
        latestArtifacts <- latestArtifactsF
      } yield
      // override non-stable version with the latest stable version
      (latestStableArtifacts ++ latestArtifacts)
        .groupBy(a => (a.groupId, a.artifactId))
        .valuesIterator
        .map(_.head)
        .toSeq
    } else latestArtifactsF
  }

  override def getArtifactByMavenReference(mavenRef: Artifact.MavenReference): Future[Option[Artifact]] =
    run(ArtifactTable.selectByMavenReference.option(mavenRef))

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
  private def insertProjectRef(ref: Project.Reference, status: GithubStatus): Future[Boolean] =
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

  override def getProject(projectRef: Project.Reference): Future[Option[Project]] =
    run(ProjectTable.selectByReference.option(projectRef))

  override def getDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]] =
    run(ArtifactDependencyTable.selectDependencyFromProject.to[Seq](projectRef))

  override def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]] =
    run(ProjectTable.selectByNewReference.to[Seq](projectRef))

  def countProjects(): Future[Long] =
    run(ProjectTable.countProjects.unique)

  override def countArtifacts(): Future[Long] =
    run(ArtifactTable.count.unique)

  def countDependencies(): Future[Long] =
    run(ArtifactDependencyTable.count.unique)

  override def getDirectDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Direct]] =
    run(ArtifactDependencyTable.selectDirectDependency.to[Seq](artifact.mavenReference))

  override def getReverseDependencies(artifact: Artifact): Future[Seq[ArtifactDependency.Reverse]] =
    run(ArtifactDependencyTable.selectReverseDependency.to[Seq](artifact.mavenReference))

  def countGithubInfo(): Future[Long] =
    run(GithubInfoTable.count.unique)

  def countProjectSettings(): Future[Long] =
    run(ProjectSettingsTable.count.unique)

  override def computeProjectDependencies(
      ref: Project.Reference,
      version: SemanticVersion
  ): Future[Seq[ProjectDependency]] =
    run(ArtifactDependencyTable.computeProjectDependencies.to[Seq]((ref, version)))

  override def computeReleaseDependencies(): Future[Seq[ReleaseDependency]] =
    run(ArtifactDependencyTable.computeReleaseDependency.to[Seq])

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] =
    if (projectDependencies.isEmpty) Future.successful(0)
    else run(ProjectDependenciesTable.insertOrUpdate.updateMany(projectDependencies))

  override def deleteProjectDependencies(ref: Project.Reference): Future[Int] =
    run(ProjectDependenciesTable.deleteBySource.run(ref))

  override def insertReleaseDependencies(releaseDependency: Seq[ReleaseDependency]): Future[Int] =
    run(ReleaseDependenciesTable.insertIfNotExists.updateMany(releaseDependency))

  override def countProjectDependents(projectRef: Project.Reference): Future[Long] =
    run(ProjectDependenciesTable.countDependents.unique(projectRef))

  override def getProjectDependencies(
      ref: Project.Reference,
      version: SemanticVersion
  ): Future[Seq[ProjectDependency]] =
    run(ProjectDependenciesTable.getDependencies.to[Seq]((ref, version)))

  override def getProjectDependents(ref: Project.Reference): Future[Seq[ProjectDependency]] =
    run(ProjectDependenciesTable.getDependents.to[Seq](ref))

  override def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] =
    run(ArtifactTable.selectOldestByProject.to[Seq])

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    run(ProjectTable.updateCreationDate.run((creationDate, ref))).map(_ => ())

  override def getAllGroupIds(): Future[Seq[Artifact.GroupId]] =
    run(ArtifactTable.selectGroupIds.to[Seq])

  override def getAllMavenReferences(): Future[Seq[Artifact.MavenReference]] =
    run(ArtifactTable.selectMavenReference.to[Seq])

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

  override def getArtifacts(
      ref: Project.Reference,
      artifactName: Artifact.Name,
      params: ArtifactsPageParams
  ): Future[Seq[Artifact]] =
    run(
      ArtifactTable
        .selectArtifactByParams(params.binaryVersions, params.preReleases)
        .to[Seq](ref, artifactName)
    )

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
