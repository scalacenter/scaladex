package ch.epfl.scala.services.storage.sql

import cats.effect.{IO, Resource}
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubInfo
import doobie.implicits._
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.services.storage.sql.tables.{GithubInfoTable, ProjectTable}
import ch.epfl.scala.utils.DoobieUtils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SqlRepo(conf: DbConf) extends DatabaseApi {
  private[sql] val (xa, flyway) = DoobieUtils.create(conf)
  def createTables(): IO[Unit] = IO(flyway.migrate())
  def dropTables(): IO[Unit] = IO(flyway.clean())

  override def insertProject(project: NewProject): Future[NewProject] = {
    for {
      p <- run(ProjectTable.insert, project)
      _ <- project.githubInfo
        .map(run(GithubInfoTable.insert(project), _))
        .getOrElse(Future.successful())
    } yield p
  }

  def insertProject(project: Project): Future[NewProject] =
    insertProject(NewProject.from(project))

  override def countProjects(): Future[Long] =
    run(ProjectTable.indexedProjects().unique)

  def countGithubInfo(): Future[Long] =
    run(GithubInfoTable.indexedGithubInfo().unique)

  private def run[A](i: A => doobie.Update0, v: A): Future[A] =
    i(v).run.transact(xa).unsafeToFuture.flatMap {
      case 1 => Future.successful(v)
      case code =>
        Future.failed(new Exception(s"Failed to insert $v (code: $code)"))
    }

  private def run[A](v: doobie.ConnectionIO[A]): Future[A] =
    v.transact(xa).unsafeToFuture
}
