package ch.epfl.scala.index.server.routes

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.local.LocalStorageRepo
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

trait ControllerBaseSuite extends AnyFunSpec with Matchers {

  private val config = ServerConfig.load()
  val env = config.api.env
  val githubUserSession = new GithubUserSession(config.session)

  val db = new DatabaseMockApi()
  val dataPaths: DataPaths = config.dataPaths
  val localStorage = new LocalStorageRepo(dataPaths)

  def insertMockData(): Unit = {
    import ch.epfl.scala.index.server.Values._
    // Insert mock data
    await(db.insertReleases(Seq(release))).get
    await(db.insertProject(project)).get
  }

  def await[A](f: Future[A]): Try[A] = Try(
    Await.result(f, Duration.Inf)
  )

  class DatabaseMockApi() extends DatabaseApi {
    private val projects = mutable.Map[NewProject.Reference, NewProject]()
    private val releases = mutable.Map[NewProject.Reference, Seq[NewRelease]]()
    private val dependencies = mutable.Seq[ReleaseDependency]()

    override def insertProject(p: NewProject): Future[Unit] =
      Future.successful(projects.addOne((p.reference, p)))
    override def insertOrUpdateProject(p: NewProject): Future[Unit] =
      Future.successful(())
    override def updateProjectForm(
        ref: NewProject.Reference,
        dataForm: NewProject.DataForm
    ): Future[Unit] = Future.successful(())
    override def findProject(
        projectRef: NewProject.Reference
    ): Future[Option[NewProject]] = Future.successful(projects.get(projectRef))
    override def insertReleases(r: Seq[NewRelease]): Future[Int] =
      Future.successful {
        val elems = r.groupBy(_.projectRef)
        releases.addAll(elems)
        elems.size
      }
    override def findReleases(
        projectRef: NewProject.Reference
    ): Future[Seq[NewRelease]] =
      Future.successful(releases.getOrElse(projectRef, Nil))

    def findReleases(
        projectRef: NewProject.Reference,
        artifactName: ArtifactName
    ): Future[Seq[NewRelease]] =
      Future.successful(
        releases
          .getOrElse(projectRef, Nil)
          .filter(_.artifactName == artifactName)
      )
    override def findDirectDependencies(
        release: NewRelease
    ): Future[List[ReleaseDependency.Direct]] = Future.successful(Nil)

    override def findReverseDependencies(
        release: NewRelease
    ): Future[List[ReleaseDependency.Reverse]] = Future.successful(Nil)

    override def insertDependencies(
        dependencies: Seq[ReleaseDependency]
    ): Future[Int] = Future.successful(dependencies.size)

    override def countProjects(): Future[Long] =
      Future.successful(projects.values.size)

    override def countReleases(): Future[Long] =
      Future.successful(releases.values.flatten.size)

    override def countDependencies(): Future[Long] =
      Future.successful(dependencies.size)

    override def getAllTopics(): Future[Seq[String]] = Future.successful(Nil)

    override def getAllPlatforms()
        : Future[Map[NewProject.Reference, Set[Platform]]] =
      Future.successful(Map.empty)

    override def getAllProjectDependencies(): Future[Seq[ProjectDependency]] =
      Future.successful(Nil)

    override def insertProjectDependencies(
        projectDependencies: Seq[ProjectDependency]
    ): Future[Int] =
      Future.successful(0)

    override def getMostDependentUponProject(
        max: Int
    ): Future[List[(NewProject, Long)]] = Future.successful(Nil)
  }
}
