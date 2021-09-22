package ch.epfl.scala.index.server.routes

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

import cats.effect.ContextShift
import cats.effect.IO
import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.local.LocalStorageRepo
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

trait CtrlTests extends AnyFunSpec with Matchers {

  private val config = ServerConfig.load()
  val githubUserSession = new GithubUserSession(config.session)

  val db = new DatabaseMockApi()
  val dataPaths: DataPaths = config.dataPaths
  val localStorage = new LocalStorageRepo(dataPaths)

  def insertMockData(): Unit = {
    import Values._
    // Insert mock data
    await(db.insertReleases(Seq(release))).get
    await(db.insertProject(project)).get
  }

  def await[A](f: Future[A]): Try[A] = Try(
    Await.result(f, Duration.Inf)
  )

  class DatabaseMockApi() extends DatabaseApi {
    private val projects = mutable.Map[Project.Reference, NewProject]()
    private val releases = mutable.Map[Project.Reference, Seq[NewRelease]]()
    private val dependencies = mutable.Seq[NewDependency]()

    override def insertProject(p: NewProject): Future[Unit] =
      Future.successful(projects.addOne((p.reference, p)))
    override def insertOrUpdateProject(p: NewProject): Future[Unit] =
      Future.successful(())
    override def updateProjectForm(
        ref: Project.Reference,
        dataForm: NewProject.DataForm
    ): Future[Unit] = Future.successful(())
    override def findProject(
        projectRef: Project.Reference
    ): Future[Option[NewProject]] = Future.successful(projects.get(projectRef))
    override def insertReleases(r: Seq[NewRelease]): Future[Int] =
      Future.successful {
        val elems = r.groupBy(_.projectRef)
        releases.addAll(elems)
        elems.size
      }
    override def findReleases(
        projectRef: Project.Reference
    ): Future[Seq[NewRelease]] =
      Future.successful(releases.getOrElse(projectRef, Nil))
    override def findDirectDependencies(
        release: NewRelease
    ): Future[List[NewDependency.Direct]] = Future.successful(Nil)

    override def findReverseDependencies(
        release: NewRelease
    ): Future[List[NewDependency.Reverse]] = Future.successful(Nil)

    override def insertDependencies(
        dependencies: Seq[NewDependency]
    ): Future[Int] = Future.successful(dependencies.size)

    override def countProjects(): Future[Long] =
      Future.successful(projects.values.size)

    override def countReleases(): Future[Long] =
      Future.successful(releases.values.flatten.size)

    override def countDependencies(): Future[Long] =
      Future.successful(dependencies.size)
  }
}
