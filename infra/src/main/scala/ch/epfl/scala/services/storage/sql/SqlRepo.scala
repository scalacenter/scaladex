package ch.epfl.scala.services.storage.sql

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
import ch.epfl.scala.utils.ScalaExtensions.TraversableOnceFutureExtension
import doobie.implicits._
import ch.epfl.scala.utils.ScalaExtensions._

class SqlRepo(conf: DbConf, xa: doobie.Transactor[IO]) extends DatabaseApi {
  private[sql] val flyway = DoobieUtils.flyway(conf)
  def migrate(): IO[Unit] = IO(flyway.migrate())
  def dropTables(): IO[Unit] = IO(flyway.clean())

  override def insertProject(project: NewProject): Future[Unit] = {
    for {
      _ <- run(ProjectTable.insert, project)
      _ <- run(ProjectUserFormTable.insert(project), project.formData)
      _ <- project.githubInfo
        .map(run(GithubInfoTable.insert(project), _))
        .getOrElse(Future.successful())
    } yield ()
  }


  override def insertOrUpdateProject(project: NewProject): Future[Unit] = {
    for {
      _ <- run(ProjectTable.insertOrUpdate, project)
      _ <- run(ProjectUserFormTable.insertOrUpdate(project), project.formData)
      _ <- project.githubInfo
        .map(run(GithubInfoTable.insertOrUpdate(project), _))
        .getOrElse(Future.successful())
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
        formData = userForm.getOrElse(NewProject.FormData.default)
      )
    )


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

  def insertRelease(release: NewRelease): Future[Int] =
    run(ReleaseTable.insert, release)

  override def insertDependencies(deps: Iterator[NewDependency]): Future[Int] = {
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

  private def run[A](i: A => doobie.Update0, v: A): Future[Int] =
    i(v).run.transact(xa).unsafeToFuture

  private def run[A](v: doobie.ConnectionIO[A]): Future[A] =
    v.transact(xa).unsafeToFuture()
}
object SqlRepo {
  val sizeOfInsertMany = 10000
}
