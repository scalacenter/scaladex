package scaladex.infra.storage.sql

import java.time.Instant

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.storage.sql.tables.ArtifactDependencyTable
import scaladex.infra.storage.sql.tables.ArtifactTable
import scaladex.infra.storage.sql.tables.GithubInfoTable
import scaladex.infra.storage.sql.tables.ProjectDependenciesTable
import scaladex.infra.storage.sql.tables.ProjectSettingsTable
import scaladex.infra.storage.sql.tables.ProjectTable
import scaladex.infra.util.DoobieUtils

class SqlDatabase(conf: DatabaseConfig, xa: doobie.Transactor[IO]) extends SchedulerDatabase with LazyLogging {

  private[sql] val flyway = DoobieUtils.flyway(conf)
  def migrate: IO[Unit] = IO(flyway.migrate())
  def dropTables: IO[Unit] = IO(flyway.clean())

  override def insertRelease(release: Artifact, dependencies: Seq[ArtifactDependency], time: Instant): Future[Unit] = {
    val unknownStatus = GithubStatus.Unknown(time)
    val insertReleaseF = run(ProjectTable.insertIfNotExists.run((release.projectRef, unknownStatus)))
      .flatMap(_ => strictRun(ArtifactTable.insert.run(release)))
      .failWithTry
      .map {
        case Failure(exception) =>
          logger.warn(s"Failed to insert ${release.artifactId} because ${exception.getMessage}")
        case Success(value) => ()
      }
    val insertDepsF = run(ArtifactDependencyTable.insert.updateMany(dependencies)).failWithTry
      .map {
        case Failure(exception) =>
          logger.warn(s"Failed to insert dependencies of ${release.artifactId} because ${exception.getMessage}")
        case Success(value) => ()
      }
    insertReleaseF.flatMap(_ => insertDepsF)
  }

  override def getAllProjectsStatuses(): Future[Map[Project.Reference, GithubStatus]] =
    run(ProjectTable.selectReferenceAndStatus.to[Seq]).map(_.toMap)

  override def getAllProjects(): Future[Seq[Project]] =
    run(ProjectTable.selectProject.to[Seq])

  override def updateReleases(releases: Seq[Artifact], newRef: Project.Reference): Future[Int] = {
    val mavenReferences = releases.map(r => newRef -> r.mavenReference)
    run(ArtifactTable.updateProjectRef.updateMany(mavenReferences))
  }

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

  override def getReleases(projectRef: Project.Reference): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProject.to[List](projectRef))

  override def getReleasesByName(
      projectRef: Project.Reference,
      artifactName: Artifact.Name
  ): Future[Seq[Artifact]] =
    run(ArtifactTable.selectArtifactByProjectAndName.to[List]((projectRef, artifactName)))

  override def countProjects(): Future[Long] =
    run(ProjectTable.countProjects.unique)

  override def countArtifacts(): Future[Long] =
    run(ArtifactTable.count.unique)

  def countDependencies(): Future[Long] =
    run(ArtifactDependencyTable.count.unique)

  override def getDirectDependencies(release: Artifact): Future[List[ArtifactDependency.Direct]] =
    run(ArtifactDependencyTable.selectDirectDependency.to[List](release.mavenReference))

  override def getReverseDependencies(release: Artifact): Future[List[ArtifactDependency.Reverse]] =
    run(ArtifactDependencyTable.selectReverseDependency.to[List](release.mavenReference))

  override def getAllTopics(): Future[List[String]] =
    run(GithubInfoTable.selectAllTopics.to[List]).map(_.flatten)

  override def getAllPlatforms(): Future[Map[Project.Reference, Set[Platform]]] =
    run(ArtifactTable.selectPlatform.to[List])
      .map(_.groupMap {
        case (org, repo, _) =>
          Project.Reference(org, repo)
      }(_._3).view.mapValues(_.toSet).toMap)

  override def getLatestProjects(limit: Int): Future[Seq[Project]] =
    run(ProjectTable.selectLatestProjects(limit).to[Seq])

  def countGithubInfo(): Future[Long] =
    run(GithubInfoTable.count.unique)

  def countProjectSettings(): Future[Long] =
    run(ProjectSettingsTable.count.unique)

  override def computeProjectDependencies(): Future[Seq[ProjectDependency]] =
    run(ArtifactDependencyTable.selectProjectDependency.to[List])

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] =
    run(ProjectDependenciesTable.insertOrUpdate.updateMany(projectDependencies))

  override def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int] =
    run(ProjectDependenciesTable.countInverseDependencies.unique(projectRef))

  override def getMostDependedUponProjects(max: Int): Future[List[(Project, Long)]] =
    for {
      resF <- run(
        ProjectDependenciesTable
          .getMostDependentUponProjects(max)
          .to[List]
      )
      res <- resF.map {
        case (ref, count) =>
          getProject(ref).map(projectOpt => projectOpt.map(_ -> count))
      }.sequence
    } yield res.flatten

  override def computeAllProjectsCreationDates(): Future[Seq[(Instant, Project.Reference)]] =
    run(ArtifactTable.selectOldestByProject.to[List])

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    run(ProjectTable.updateCreationDate.run((creationDate, ref))).map(_ => ())

  // to use only when inserting one element or updating one element
  // when expecting a row to be modified
  private def strictRun(update: doobie.ConnectionIO[Int], expectedRows: Int = 1): Future[Unit] =
    update.transact(xa).unsafeToFuture().map {
      case `expectedRows` => ()
      case other =>
        throw new Exception(
          s"Only $other rows were affected (expected: $expectedRows)"
        )
    }

  private def run(update: doobie.Update0): Future[Unit] =
    update.run.transact(xa).unsafeToFuture().map(_ => ())

  private def run[A](v: doobie.ConnectionIO[A]): Future[A] =
    v.transact(xa).unsafeToFuture()
}
object SqlDatabase {
  val sizeOfInsertMany = 10000
}
