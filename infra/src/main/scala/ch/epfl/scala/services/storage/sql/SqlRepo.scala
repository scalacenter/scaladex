package ch.epfl.scala.services.storage.sql

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import cats.effect.IO
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.services.storage.sql.tables.GithubInfoTable
import ch.epfl.scala.services.storage.sql.tables.ProjectDependenciesTable
import ch.epfl.scala.services.storage.sql.tables.ProjectTable
import ch.epfl.scala.services.storage.sql.tables.ProjectUserFormTable
import ch.epfl.scala.services.storage.sql.tables.ReleaseDependencyTable
import ch.epfl.scala.services.storage.sql.tables.ReleaseTable
import ch.epfl.scala.utils.DoobieUtils
import ch.epfl.scala.utils.ScalaExtensions._
import doobie.implicits._

class SqlRepo(conf: DatabaseConfig, xa: doobie.Transactor[IO])
    extends DatabaseApi {
  private[sql] val flyway = DoobieUtils.flyway(conf)
  def migrate: IO[Unit] = IO(flyway.migrate())
  def dropTables: IO[Unit] = IO(flyway.clean())

  override def insertProject(project: NewProject): Future[Unit] = {
    for {
      _ <- strictRun(ProjectTable.insert(project))
      _ <- strictRun(ProjectUserFormTable.insert(project)(project.dataForm))
      _ <- project.githubInfo
        .map(githubInfo =>
          strictRun(GithubInfoTable.insert(project)(githubInfo))
        )
        .getOrElse(Future.successful(()))
    } yield ()
  }

  // this insertProjects wont fail if one insert fails
  def insertProjectsWithFailures(
      projects: Seq[NewProject]
  ): Future[Seq[(NewProject, Try[Unit])]] =
    projects.map(p => insertProject(p).failWithTry.map((p, _))).sequence

  def insertReleasesWithFailures(
      releases: Seq[NewRelease]
  ): Future[Seq[(NewRelease, Try[Unit])]] =
    releases.map(r => insertRelease(r).failWithTry.map((r, _))).sequence

  override def insertOrUpdateProject(project: NewProject): Future[Unit] = {
    for {
      _ <- run(ProjectTable.insertOrUpdate(project))
      _ <- run(ProjectUserFormTable.insertOrUpdate(project)(project.dataForm))
      _ <- project.githubInfo
        .map(githubInfo =>
          run(GithubInfoTable.insertOrUpdate(project)(githubInfo))
        )
        .getOrElse(Future.successful(()))
    } yield ()
  }

  override def updateProjectForm(
      ref: NewProject.Reference,
      dataForm: NewProject.DataForm
  ): Future[Unit] = {
    for {
      projectOpt <- run(
        ProjectTable.selectOne(ref.organization, ref.repository)
      )
      _ <- projectOpt
        .map(p => run(ProjectUserFormTable.update(p)(dataForm)))
        .getOrElse(Future.successful(()))
    } yield ()
  }
  override def findProject(
      projectRef: NewProject.Reference
  ): Future[Option[NewProject]] = {
    for {
      project <- run(
        ProjectTable.selectOne(projectRef.organization, projectRef.repository)
      )
      userForm <- run(
        ProjectUserFormTable.selectOne(
          projectRef.organization,
          projectRef.repository
        )
      )
      githubInfoTable <- project
        .map(p => run(GithubInfoTable.selectOne(p.organization, p.repository)))
        .getOrElse(Future.successful(None))
    } yield project.map(
      _.copy(
        githubInfo = githubInfoTable,
        dataForm = userForm.getOrElse(NewProject.DataForm.default)
      )
    )
  }

  def insertProject(project: Project): Future[Unit] =
    insertProject(NewProject.from(project))

  override def insertReleases(releases: Seq[NewRelease]): Future[Int] =
    releases
      .grouped(SqlRepo.sizeOfInsertMany)
      .map(r => run(ReleaseTable.insertMany(r)))
      .sequence
      .map(_.sum)

  override def findReleases(
      projectRef: NewProject.Reference
  ): Future[Seq[NewRelease]] =
    run(ReleaseTable.selectReleases(projectRef).to[List])

  def insertRelease(release: NewRelease): Future[Unit] =
    strictRun(ReleaseTable.insert(release))

  override def findReleases(
      projectRef: NewProject.Reference,
      artifactName: NewRelease.ArtifactName
  ): Future[Seq[NewRelease]] =
    run(ReleaseTable.selectReleases(projectRef, artifactName).to[List])

  override def insertDependencies(deps: Seq[ReleaseDependency]): Future[Int] = {
    deps
      .grouped(SqlRepo.sizeOfInsertMany)
      .map(d => run(ReleaseDependencyTable.insertMany(d)))
      .sequence
      .map(_.sum)
  }

  override def countProjects(): Future[Long] =
    run(ProjectTable.indexedProjects().unique)

  override def countReleases(): Future[Long] =
    run(ReleaseTable.indexedReleased().unique)

  override def countDependencies(): Future[Long] =
    run(ReleaseDependencyTable.indexedDependencies().unique)

  def findDependencies(release: NewRelease): Future[List[ReleaseDependency]] =
    run(ReleaseDependencyTable.find(release.maven).to[List])

  override def findDirectDependencies(
      release: NewRelease
  ): Future[List[ReleaseDependency.Direct]] =
    run(ReleaseDependencyTable.selectDirectDependencies(release).to[List])

  override def findReverseDependencies(
      release: NewRelease
  ): Future[List[ReleaseDependency.Reverse]] =
    run(ReleaseDependencyTable.selectReverseDependencies(release).to[List])

  override def getAllTopics(): Future[List[String]] =
    run(GithubInfoTable.selectAllTopics().to[List]).map(_.flatten)

  override def getAllPlatforms()
      : Future[Map[NewProject.Reference, Set[Platform]]] =
    run(ReleaseTable.selectPlatform().to[List])
      .map(_.groupMap { case (org, repo, _) =>
        NewProject.Reference(org, repo)
      }(_._3).view.mapValues(_.toSet).toMap)

  def countGithubInfo(): Future[Long] =
    run(GithubInfoTable.indexedGithubInfo().unique)

  def countProjectDataForm(): Future[Long] =
    run(ProjectUserFormTable.indexedProjectUserForm().unique)

  override def getAllProjectDependencies(): Future[Seq[ProjectDependency]] =
    run(ReleaseDependencyTable.getAllProjectDependencies().to[List])

  override def insertProjectWithDependentUponProjects(
      projectDependencies: Seq[ProjectDependency]
  ): Future[Int] =
    run(ProjectDependenciesTable.insertMany(projectDependencies))

  override def getMostDependentUponProject(
      max: Int
  ): Future[List[(NewProject, Long)]] = {
    for {
      resF <- run(
        ProjectDependenciesTable
          .getMostDependentUponProjects(max)
          .to[List]
      )
      res <- resF.map { case (ref, count) =>
        findProject(ref).map(projectOpt => projectOpt.map(_ -> count))
      }.sequence
    } yield res.flatten

  }

  override def updateCreatedIn(ref: NewProject.Reference): Future[Unit] = {
    for {
      project <- findReleases(ref)

    } yield ()
  }

  // to use only when inserting one element or updating one element
  // when expecting a row to be modified
  private def strictRun(
      update: doobie.Update0,
      expectedRows: Int = 1
  ): Future[Unit] =
    update.run.transact(xa).unsafeToFuture().map {
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
