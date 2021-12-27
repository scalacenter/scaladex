package ch.epfl.scala.index.server.routes

import java.time.Instant

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.config.ServerConfig
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubStatus
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.service.SchedulerDatabase
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.local.LocalStorageRepo
import scaladex.core.test.Values

trait ControllerBaseSuite extends AnyFunSpec with Matchers {
  private val config = ServerConfig.load()
  val env = config.api.env
  val githubUserSession = new GithubUserSession(config.session)

  val db: SchedulerDatabase = new DatabaseMockApi()
  val dataPaths: DataPaths = config.dataPaths
  val localStorage = new LocalStorageRepo(dataPaths)

  def insertMockData(): Future[Unit] = {
    import Values.PlayJsonExtra._
    // Insert mock data
    implicit val ec = ExecutionContext.global
    for {
      _ <- db.insertRelease(artifact, Seq.empty, Values.now)
      _ <- db.updateProjectCreationDate(reference, creationDate)
      _ <- db.updateGithubInfoAndStatus(reference, githubInfo, GithubStatus.Ok(Values.now))
    } yield ()
  }

  class DatabaseMockApi() extends SchedulerDatabase {

    override def createMovedProject(
        ref: Project.Reference,
        githubInfo: GithubInfo,
        githubStatus: GithubStatus.Moved
    ): Future[Unit] = ???

    private val projects = mutable.Map[Project.Reference, Project]()
    private val releases = mutable.Map[Project.Reference, Seq[Artifact]]()
    private val dependencies = mutable.Seq[ArtifactDependency]()

    override def insertRelease(
        release: Artifact,
        dependencies: Seq[ArtifactDependency],
        now: Instant
    ): Future[Unit] = {
      val ref = release.projectRef
      if (!projects.contains(ref)) projects.addOne(ref -> Project.default(ref, now = now))
      releases.addOne(ref -> (releases.getOrElse(ref, Seq.empty) :+ release))
      dependencies.appendedAll(dependencies)
      Future.successful(())
    }

    override def updateProjectSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] =
      Future.successful(())

    override def findProject(projectRef: Project.Reference): Future[Option[Project]] =
      Future.successful(projects.get(projectRef))

    override def findReleases(projectRef: Project.Reference): Future[Seq[Artifact]] =
      Future.successful(releases.getOrElse(projectRef, Nil))

    def findReleases(projectRef: Project.Reference, artifactName: Artifact.Name): Future[Seq[Artifact]] =
      Future.successful(
        releases
          .getOrElse(projectRef, Nil)
          .filter(_.artifactName == artifactName)
      )

    override def findDirectDependencies(release: Artifact): Future[List[ArtifactDependency.Direct]] =
      Future.successful(Nil)

    override def findReverseDependencies(release: Artifact): Future[List[ArtifactDependency.Reverse]] =
      Future.successful(Nil)

    override def countProjects(): Future[Long] =
      Future.successful(projects.values.size)

    override def countArtifacts(): Future[Long] =
      Future.successful(releases.values.flatten.size)

    override def countDependencies(): Future[Long] =
      Future.successful(dependencies.size)

    override def getAllTopics(): Future[Seq[String]] = Future.successful(Nil)

    override def getAllPlatforms(): Future[Map[Project.Reference, Set[Platform]]] =
      Future.successful(Map.empty)

    override def getLatestProjects(limit: Int): Future[Seq[Project]] =
      Future.successful(Nil)

    override def getMostDependentUponProject(max: Int): Future[List[(Project, Long)]] =
      Future.successful(Nil)

    override def getAllProjectRef(): Future[Seq[Project.Reference]] = ???

    override def getAllProjects(): Future[Seq[Project]] = ???

    override def updateGithubInfoAndStatus(
        ref: Project.Reference,
        githubInfo: GithubInfo,
        githubStatus: GithubStatus
    ): Future[Unit] =
      Future.successful(
        projects.update(ref, projects(ref).copy(githubInfo = Some(githubInfo), githubStatus = githubStatus))
      )

    override def updateGithubStatus(ref: Project.Reference, githubStatus: GithubStatus): Future[Unit] = ???

    override def computeProjectDependencies(): Future[Seq[ProjectDependency]] = ???

    override def computeAllProjectsCreationDate(): Future[Seq[(Instant, Project.Reference)]] = ???

    override def updateProjectCreationDate(ref: Project.Reference, creationDate: Instant): Future[Unit] =
      Future.successful(projects.update(ref, projects(ref).copy(creationDate = Some(creationDate))))

    override def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int] = ???

    override def countInverseProjectDependencies(projectRef: Project.Reference): Future[Int] = ???

    override def updateReleases(release: Seq[Artifact], newRef: Project.Reference): Future[Int] = ???
  }
}
