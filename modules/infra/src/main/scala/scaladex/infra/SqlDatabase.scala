package scaladex.infra

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.UserState
import scaladex.core.service.SchedulerDatabase
import scaladex.infra.config.PostgreSQLConfig
import scaladex.infra.sql.ArtifactDependencyTable
import scaladex.infra.sql.ArtifactTable
import scaladex.infra.sql.DoobieUtils
import scaladex.infra.sql.GithubInfoTable
import scaladex.infra.sql.ProjectDependenciesTable
import scaladex.infra.sql.ProjectSettingsTable
import scaladex.infra.sql.ProjectTable
import scaladex.infra.sql.UserSessionsTable

class SqlDatabase(conf: PostgreSQLConfig, xa: doobie.Transactor[IO]) extends SchedulerDatabase with LazyLogging {

  private val flyway = DoobieUtils.flyway(conf)
  def migrate: IO[Unit] = IO(flyway.migrate())
  def dropTables: IO[Unit] = IO(flyway.clean())

  override def insertArtifact(
      artifact: Artifact,
      dependencies: Seq[ArtifactDependency],
      time: Instant
  ): Future[Unit] = {
    val unknownStatus = GithubStatus.Unknown(time)
    val insertArtifactF = insertProjectRef(artifact.projectRef, unknownStatus)
      .flatMap(_ => run(ArtifactTable.insertIfNotExist.run(artifact)))
    val insertDepsF = insertDependencies(dependencies)
    insertArtifactF.flatMap(_ => insertDepsF).map(_ => ())
  }

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

  override def insertArtifacts(artifacts: Seq[Artifact]): Future[Unit] =
    run(ArtifactTable.insertIfNotExist.updateMany(artifacts)).map(_ => ())

  override def insertDependencies(dependencies: Seq[ArtifactDependency]): Future[Unit] =
    run(ArtifactDependencyTable.insertIfNotExist.updateMany(dependencies)).map(_ => ())

  // return true if inserted, false if it already existed
  private def insertProjectRef(ref: Project.Reference, status: GithubStatus): Future[Boolean] =
    run(ProjectTable.insertIfNotExists.run((ref, status))).map(x => x >= 1)

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] =
    run(ProjectTable.selectReferenceAndStatus.to[Seq]).map(_.toMap)

  override def getAllProjects(): Future[Seq[Project]] =
    run(ProjectTable.selectProject.to[Seq])

  override def updateArtifacts(artifacts: Seq[Artifact], newRef: Project.Reference): Future[Int] = {
    val mavenReferences = artifacts.map(r => newRef -> r.mavenReference)
    run(ArtifactTable.updateProjectRef.updateMany(mavenReferences))
  }

  override def updateArtifactReleaseDate(reference: Artifact.MavenReference, releaseDate: Instant): Future[Int] =
    run(ArtifactTable.updateReleaseDate.run((releaseDate, reference)))

  override def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): Future[Unit] =
    run(ProjectTable.updateGithubStatus.run(githubStatus, ref)).map(_ => ())

  override def updateGithubInfoAndStatus(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    for {
      _ <- updateGithubStatus(ref, githubStatus)
      _ <- run(GithubInfoTable.insertOrUpdate.run((ref, githubInfo, githubInfo)))
    } yield ()

  override def moveProject(
      ref: Project.Reference,
      githubInfo: GithubInfo,
      status: GithubStatus.Moved
  ): Future[Unit] =
    for {
      oldProject <- getProject(ref)
      _ <- updateGithubStatus(ref, status)
      _ <- run(ProjectTable.insertIfNotExists.run((status.destination, GithubStatus.Ok(status.updateDate))))
      _ <- updateProjectSettings(status.destination, oldProject.map(_.settings).getOrElse(Project.Settings.default))
      _ <- run(GithubInfoTable.insertOrUpdate.run(status.destination, githubInfo, githubInfo))
    } yield ()

  override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] =
    run(ProjectSettingsTable.insertOrUpdate.run((ref, settings, settings))).map(_ => ())

  override def getProject(projectRef: Project.Reference): Future[Option[Project]] =
    run(ProjectTable.selectByReference.option(projectRef))

  override def getArtifacts(projectRef: Project.Reference): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProject.to[Seq](projectRef))

  override def getDependencies(projectRef: Project.Reference): Future[Seq[ArtifactDependency]] =
    run(ArtifactDependencyTable.selectDependencyFromProject.to[Seq](projectRef))

  override def getFormerReferences(projectRef: Project.Reference): Future[Seq[Project.Reference]] =
    run(ProjectTable.selectByNewReference.to[Seq](projectRef))

  override def getArtifactsByName(
      projectRef: Project.Reference,
      artifactName: Artifact.Name
  ): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProjectAndName.to[Seq]((projectRef, artifactName)))

  override def getArtifactByMavenReference(mavenRef: Artifact.MavenReference): Future[Option[Artifact]] =
    run(ArtifactTable.selectByMavenReference.option(mavenRef))

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

  override def computeProjectDependencies(): Future[Seq[ProjectDependency]] =
    run(ArtifactDependencyTable.computeProjectDependency.to[Seq])

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] =
    run(ProjectDependenciesTable.insertOrUpdate.updateMany(projectDependencies))

  override def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int] =
    run(ProjectDependenciesTable.countInverseDependencies.unique(projectRef))

  override def deleteDependenciesOfMovedProject(): Future[Unit] =
    for {
      moved <- run(ProjectTable.selectProjectByGithubStatus.to[Seq]("Moved"))
      _ <- run(ProjectDependenciesTable.deleteBySource.updateMany(moved))
      _ <- run(ProjectDependenciesTable.deleteByTarget.updateMany(moved))
    } yield ()

  override def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] =
    run(ArtifactTable.selectOldestByProject.to[Seq])

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    run(ProjectTable.updateCreationDate.run((creationDate, ref))).map(_ => ())

  override def getAllGroupIds(): Future[Seq[Artifact.GroupId]] =
    run(ArtifactTable.selectGroupIds.to[Seq])

  override def getAllMavenReferences(): Future[Seq[Artifact.MavenReference]] =
    run(ArtifactTable.selectMavenReference.to[Seq])

  override def insertSession(userId: UUID, userState: UserState): Future[Unit] =
    run(UserSessionsTable.insertOrUpdate.run((userId, userState)).map(_ => ()))

  override def getSession(userId: UUID): Future[Option[UserState]] =
    run(UserSessionsTable.selectUserSessionById.to[Seq](userId)).map(_.headOption)

  override def getAllSessions(): Future[Seq[(UUID, UserState)]] =
    run(UserSessionsTable.selectAllUserSessions.to[Seq])

  override def getAllMavenReferencesWithNoReleaseDate(): Future[Seq[Artifact.MavenReference]] =
    run(ArtifactTable.selectMavenReferenceWithNoReleaseDate.to[Seq])

  private def run[A](v: doobie.ConnectionIO[A]): Future[A] =
    v.transact(xa).unsafeToFuture()
}
