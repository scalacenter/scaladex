package ch.epfl.scala.services.storage.sql

import java.time.Instant

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import cats.effect.IO
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.services.storage.sql.tables.GithubInfoTable
import ch.epfl.scala.services.storage.sql.tables.ProjectDependenciesTable
import ch.epfl.scala.services.storage.sql.tables.ProjectTable
import ch.epfl.scala.services.storage.sql.tables.ProjectUserFormTable
import ch.epfl.scala.services.storage.sql.tables.ReleaseDependencyTable
import ch.epfl.scala.services.storage.sql.tables.ReleaseTable
import ch.epfl.scala.utils.DoobieUtils
import ch.epfl.scala.utils.ScalaExtensions._
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._

class SqlRepo(conf: DatabaseConfig, xa: doobie.Transactor[IO]) extends SchedulerDatabase with LazyLogging {

  private[sql] val flyway = DoobieUtils.flyway(conf)
  def migrate: IO[Unit] = IO(flyway.migrate())
  def dropTables: IO[Unit] = IO(flyway.clean())

  override def insertRelease(release: NewRelease, dependencies: Seq[ReleaseDependency], time: Instant): Future[Unit] = {
    val unknownStatus = GithubStatus.Unknown(time)
    val insertReleaseF = run(ProjectTable.insertIfNotExists.run((release.projectRef, unknownStatus)))
      .flatMap(_ => strictRun(ReleaseTable.insert.run(release)))
      .failWithTry
      .map {
        case Failure(exception) =>
          logger.warn(s"Failed to insert ${release.maven.name} because ${exception.getMessage}")
        case Success(value) => ()
      }
    val insertDepsF = run(ReleaseDependencyTable.insert.updateMany(dependencies)).failWithTry
      .map {
        case Failure(exception) =>
          logger.warn(s"Failed to insert dependencies of ${release.maven.name} because ${exception.getMessage}")
        case Success(value) => ()
      }
    insertReleaseF.flatMap(_ => insertDepsF)
  }

  override def getAllProjectRef(): Future[Seq[Project.Reference]] =
    run(ProjectTable.selectAllProjectRef().to[List])

  override def getAllProjects(): Future[Seq[Project]] =
    run(ProjectTable.selectAllProjects.to[Seq])

  override def updateReleases(releases: Seq[NewRelease], newRef: Project.Reference): Future[Int] = {
    val mavenReferences = releases.map(r => newRef -> r.maven)
    run(ReleaseTable.updateProjectRef.updateMany(mavenReferences))
  }

  override def updateGithubStatus(p: Project.Reference, githubStatus: GithubStatus): Future[Unit] =
    run(ProjectTable.updateGithubStatus.run(githubStatus, p)).map(_ => ())

  override def updateGithubInfoAndStatus(
      p: Project.Reference,
      githubInfo: GithubInfo,
      githubStatus: GithubStatus
  ): Future[Unit] =
    for {
      _ <- updateGithubStatus(p, githubStatus)
      _ <- run(GithubInfoTable.insertOrUpdate(p)(githubInfo))
    } yield ()

  override def insertOrUpdateProject(project: Project): Future[Unit] =
    for {
      _ <- run(ProjectTable.insertIfNotExists.run((project.reference, project.githubStatus)))
      _ <- updateGithubStatus(project.reference, project.githubStatus)
      _ <- updateProjectForm(project.reference, project.dataForm)
      _ <- project.githubInfo
        .map(githubInfo => run(GithubInfoTable.insertOrUpdate(project.reference)(githubInfo)))
        .getOrElse(Future.successful(()))
    } yield ()

  override def createMovedProject(
      oldRef: Project.Reference,
      info: GithubInfo,
      githubStatus: GithubStatus.Moved
  ): Future[Unit] =
    for {
      oldProject <- findProject(oldRef)
      _ <- updateGithubStatus(oldRef, githubStatus)
      newProject = Project(
        Project.Organization(info.owner),
        Project.Repository(info.name),
        created = oldProject.flatMap(_.created), // todo:  from github
        GithubStatus.Ok(githubStatus.when),
        Some(info),
        oldProject.map(_.dataForm).getOrElse(Project.DataForm.default)
      )
      _ <- insertOrUpdateProject(newProject)
    } yield ()

  override def updateProjectForm(ref: Project.Reference, dataForm: Project.DataForm): Future[Unit] =
    run(ProjectUserFormTable.insertOrUpdate(ref)(dataForm))

  override def findProject(projectRef: Project.Reference): Future[Option[Project]] =
    run(ProjectTable.selectOne(projectRef).option)

  override def findReleases(projectRef: Project.Reference): Future[Seq[NewRelease]] =
    run(ReleaseTable.selectReleases(projectRef).to[List])

  override def findReleases(
      projectRef: Project.Reference,
      artifactName: NewRelease.ArtifactName
  ): Future[Seq[NewRelease]] =
    run(ReleaseTable.selectReleases(projectRef, artifactName).to[List])

  override def countProjects(): Future[Long] =
    run(ProjectTable.indexedProjects().unique)

  override def countReleases(): Future[Long] =
    run(ReleaseTable.indexedReleased().unique)

  override def countDependencies(): Future[Long] =
    run(ReleaseDependencyTable.indexedDependencies().unique)

  def findDependencies(release: NewRelease): Future[List[ReleaseDependency]] =
    run(ReleaseDependencyTable.find(release.maven).to[List])

  override def findDirectDependencies(release: NewRelease): Future[List[ReleaseDependency.Direct]] =
    run(ReleaseDependencyTable.selectDirectDependencies(release).to[List])

  override def findReverseDependencies(release: NewRelease): Future[List[ReleaseDependency.Reverse]] =
    run(ReleaseDependencyTable.selectReverseDependencies(release).to[List])

  override def getAllTopics(): Future[List[String]] =
    run(GithubInfoTable.selectAllTopics().to[List]).map(_.flatten)

  override def getAllPlatforms(): Future[Map[Project.Reference, Set[Platform]]] =
    run(ReleaseTable.selectPlatform().to[List])
      .map(_.groupMap {
        case (org, repo, _) =>
          Project.Reference(org, repo)
      }(_._3).view.mapValues(_.toSet).toMap)

  override def getLatestProjects(limit: Int): Future[Seq[Project]] =
    run(ProjectTable.selectLatestProjects(limit).to[Seq])

  def countGithubInfo(): Future[Long] =
    run(GithubInfoTable.indexedGithubInfo().unique)

  def countProjectDataForm(): Future[Long] =
    run(ProjectUserFormTable.indexedProjectUserForm().unique)

  override def computeProjectDependencies(): Future[Seq[ProjectDependency]] =
    run(ReleaseDependencyTable.getAllProjectDependencies().to[List])

  override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] =
    run(ProjectDependenciesTable.insertMany(projectDependencies))

  override def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int] =
    run(ProjectDependenciesTable.countInverseDependencies(projectRef))

  override def getMostDependentUponProject(max: Int): Future[List[(Project, Long)]] =
    for {
      resF <- run(
        ProjectDependenciesTable
          .getMostDependentUponProjects(max)
          .to[List]
      )
      res <- resF.map {
        case (ref, count) =>
          findProject(ref).map(projectOpt => projectOpt.map(_ -> count))
      }.sequence
    } yield res.flatten

  override def computeAllProjectsCreationDate(): Future[Seq[(Instant, Project.Reference)]] =
    run(ReleaseTable.findOldestReleasesPerProjectReference().to[List])

  override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
    run(ProjectTable.updateCreated.run((creationDate, ref))).map(_ => ())

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
object SqlRepo {
  val sizeOfInsertMany = 10000
}
