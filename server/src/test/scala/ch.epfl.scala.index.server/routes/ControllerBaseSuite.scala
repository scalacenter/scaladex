package ch.epfl.scala.index.server.routes

import java.time.Instant

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.local.LocalStorageRepo
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

trait ControllerBaseSuite extends AnyFunSpec with Matchers {
  private val config = ServerConfig.load()
  val env = config.api.env
  val githubUserSession = new GithubUserSession(config.session)

  val db: SchedulerDatabase = new DatabaseMockApi()
  val dataPaths: DataPaths = config.dataPaths
  val localStorage = new LocalStorageRepo(dataPaths)

  def insertMockData(): Future[Unit] = {
    import ch.epfl.scala.index.server.Values._
    // Insert mock data
    implicit val ec = ExecutionContext.global
    for {
      _ <- db.insertRelease(release, Seq.empty, now)
      _ <- db.updateProjectCreationDate(project.reference, project.created.get)
      _ <- db.updateGithubInfoAndStatus(project.reference, project.githubInfo.get, project.githubStatus)
    } yield ()
  }

  class DatabaseMockApi() extends SchedulerDatabase {

    override def insertOrUpdateProject(p: NewProject): Future[Unit] = ???

    override def createMovedProject(
        ref: NewProject.Reference,
        githubInfo: GithubInfo,
        githubStatus: GithubStatus.Moved
    ): Future[Unit] = ???

    private val projects = mutable.Map[NewProject.Reference, NewProject]()
    private val releases = mutable.Map[NewProject.Reference, Seq[NewRelease]]()
    private val dependencies = mutable.Seq[ReleaseDependency]()

    override def insertRelease(
        release: NewRelease,
        dependencies: Seq[ReleaseDependency],
        now: Instant
    ): Future[Unit] = {
      val ref = release.projectRef
      if (!projects.contains(ref)) projects.addOne(ref -> NewProject.default(ref, now = now))
      releases.addOne(ref -> (releases.getOrElse(ref, Seq.empty) :+ release))
      dependencies.appendedAll(dependencies)
      Future.successful(())
    }

    override def updateProjectForm(ref: NewProject.Reference, dataForm: NewProject.DataForm): Future[Unit] =
      Future.successful(())

    override def findProject(projectRef: NewProject.Reference): Future[Option[NewProject]] =
      Future.successful(projects.get(projectRef))

    override def findReleases(projectRef: NewProject.Reference): Future[Seq[NewRelease]] =
      Future.successful(releases.getOrElse(projectRef, Nil))

    def findReleases(projectRef: NewProject.Reference, artifactName: ArtifactName): Future[Seq[NewRelease]] =
      Future.successful(
        releases
          .getOrElse(projectRef, Nil)
          .filter(_.artifactName == artifactName)
      )

    override def findDirectDependencies(release: NewRelease): Future[List[ReleaseDependency.Direct]] =
      Future.successful(Nil)

    override def findReverseDependencies(release: NewRelease): Future[List[ReleaseDependency.Reverse]] =
      Future.successful(Nil)

    override def countProjects(): Future[Long] =
      Future.successful(projects.values.size)

    override def countReleases(): Future[Long] =
      Future.successful(releases.values.flatten.size)

    override def countDependencies(): Future[Long] =
      Future.successful(dependencies.size)

    override def getAllTopics(): Future[Seq[String]] = Future.successful(Nil)

    override def getAllPlatforms(): Future[Map[NewProject.Reference, Set[Platform]]] =
      Future.successful(Map.empty)

    override def getLatestProjects(limit: Int): Future[Seq[NewProject]] =
      Future.successful(Nil)

    override def getMostDependentUponProject(max: Int): Future[List[(NewProject, Long)]] =
      Future.successful(Nil)

    override def getAllProjectRef(): Future[Seq[NewProject.Reference]] = ???

    override def getAllProjects(): Future[Seq[NewProject]] = ???

    override def updateGithubInfoAndStatus(
        ref: NewProject.Reference,
        githubInfo: GithubInfo,
        githubStatus: GithubStatus
    ): Future[Unit] =
      Future.successful(
        projects.update(ref, projects(ref).copy(githubInfo = Some(githubInfo), githubStatus = githubStatus))
      )

    override def updateGithubStatus(ref: NewProject.Reference, githubStatus: GithubStatus): Future[Unit] = ???

    override def computeProjectDependencies(): Future[Seq[ProjectDependency]] = ???

    override def computeAllProjectsCreationDate(): Future[Seq[(Instant, NewProject.Reference)]] = ???

    override def updateProjectCreationDate(ref: NewProject.Reference, creationDate: Instant): Future[Unit] =
      Future.successful(projects.update(ref, projects(ref).copy(created = Some(creationDate))))

    override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] = ???

    override def countInverseProjectDependencies(projectRef: NewProject.Reference): Future[Int] = ???
  }
}
