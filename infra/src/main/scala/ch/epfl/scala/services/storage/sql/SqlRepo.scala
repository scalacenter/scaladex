package ch.epfl.scala.services.storage.sql

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import cats.effect.IO
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.services.storage.sql.tables.DependenciesTable
import ch.epfl.scala.services.storage.sql.tables.GithubInfoTable
import ch.epfl.scala.services.storage.sql.tables.ProjectTable
import ch.epfl.scala.services.storage.sql.tables.ProjectUserFormTable
import ch.epfl.scala.services.storage.sql.tables.ReleaseTable
import ch.epfl.scala.utils.DoobieUtils
import ch.epfl.scala.utils.ScalaExtensions._
import doobie.implicits._

class SqlRepo(conf: DbConf, xa: doobie.Transactor[IO]) extends DatabaseApi {
  private[sql] val flyway = DoobieUtils.flyway(conf)
  def migrate(): IO[Unit] = IO(flyway.migrate())
  def dropTables(): IO[Unit] = IO(flyway.clean())

  override def insertProject(project: NewProject): Future[Unit] = {
    for {
      _ <- run(ProjectTable.insert, project)
      _ <- run(ProjectUserFormTable.insert(project), project.dataForm)
      _ <- project.githubInfo
        .map(run(GithubInfoTable.insert(project), _))
        .getOrElse(Future.successful())
    } yield ()
  }

  // this insertProjects wont fail if one insert fails
  def insertProjectsWithFailures(
      projects: Seq[NewProject]
  ): Future[Seq[(NewProject, Try[Unit])]] =
    projects.map(p => insertProject(p).failWithTry.map((p, _))).sequence

  def insertReleasesWithFailures(
      releases: Seq[NewRelease]
  ): Future[Seq[(NewRelease, Try[NewRelease])]] =
    releases.map(r => insertRelease(r).failWithTry.map((r, _))).sequence

  override def insertOrUpdateProject(project: NewProject): Future[Unit] = {
    for {
      _ <- run(ProjectTable.insertOrUpdate, project)
      _ <- run(ProjectUserFormTable.insertOrUpdate(project), project.dataForm)
      _ <- project.githubInfo
        .map(run(GithubInfoTable.insertOrUpdate(project), _))
        .getOrElse(Future.successful())
    } yield ()
  }

  override def updateProjectForm(
      ref: Project.Reference,
      dataForm: NewProject.DataForm
  ): Future[Unit] = {
    for {
      projectOpt <- run(ProjectTable.selectOne(ref.org, ref.repo))
      _ <- projectOpt
        .map(p => run(ProjectUserFormTable.update(p), dataForm))
        .getOrElse(
          Future.successful(println(s"Cannot update user data form for $ref"))
        )
    } yield ()
  }
  override def findProject(
      projectRef: Project.Reference
  ): Future[Option[NewProject]] =
    for {
      project <- run(ProjectTable.selectOne(projectRef.org, projectRef.repo))
      userForm <- run(
        ProjectUserFormTable.selectOne(projectRef.org, projectRef.repo)
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

  def insertProject(project: Project): Future[Unit] =
    insertProject(NewProject.from(project))

  override def insertReleases(releases: Seq[NewRelease]): Future[Int] =
    releases
      .grouped(SqlRepo.sizeOfInsertMany)
      .map(r => run(ReleaseTable.insertMany(r)))
      .sequence
      .map(_.sum)

  override def findReleases(
      projectRef: Project.Reference
  ): Future[Seq[NewRelease]] =
    run(ReleaseTable.selectReleases(projectRef).to[List])

  def insertRelease(release: NewRelease): Future[NewRelease] =
    run(ReleaseTable.insert, release)

  override def insertDependencies(deps: Seq[NewDependency]): Future[Int] = {
    deps
      .grouped(SqlRepo.sizeOfInsertMany)
      .map(d => run(DependenciesTable.insertMany(d)))
      .sequence
      .map(_.sum)
  }

  override def countProjects(): Future[Long] =
    run(ProjectTable.indexedProjects().unique)

  override def countReleases(): Future[Long] =
    run(ReleaseTable.indexedReleased().unique)

  override def countDependencies(): Future[Long] =
    run(DependenciesTable.indexedDependencies().unique)

  def countGithubInfo(): Future[Long] =
    run(GithubInfoTable.indexedGithubInfo().unique)

  def countProjectDataForm(): Future[Long] =
    run(ProjectUserFormTable.indexedProjectUserForm().unique)

  // to use only when inserting one element or updating one element
  // when expecting a row to be modified
  private def run[A](i: A => doobie.Update0, v: A): Future[A] =
    i(v).run.transact(xa).unsafeToFuture.flatMap {
      case 1 => Future.successful(v)
      case code =>
        Future.failed(new Exception(s"Failed to insert $v (code: $code)"))
    }

  private def run[A](v: doobie.ConnectionIO[A]): Future[A] =
    v.transact(xa).unsafeToFuture()
}
object SqlRepo {
  val sizeOfInsertMany = 10000
}
