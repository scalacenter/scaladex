package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.GithubClient
import scaladex.infra.SonatypeClient
import scaladex.view.SchedulerStatus

class SchedulerService(
    database: SchedulerDatabase,
    searchEngine: SearchEngine,
    githubClientOpt: Option[GithubClient],
    sonatypeClient: SonatypeClient,
    publishProcess: PublishProcess
) extends LazyLogging {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  private val schedulers = Seq(
    Scheduler("update-project-dependencies", updateProjectDependencies, 1.hour),
    Scheduler("update-project-creation-date", updateProjectCreationDate, 30.minutes),
    new SearchSynchronizer(database, searchEngine),
    new MovedArtifactsSynchronizer(database),
    new SonatypeSynchronizer(database, sonatypeClient, publishProcess)
  ) ++ githubClientOpt.map(client => new GithubUpdater(database, client))
  private val schedulerMap = schedulers.map(s => s.name -> s).toMap

  def startAll(): Unit =
    schedulerMap.values.foreach(_.start())

  def start(name: String): Unit =
    schedulerMap.get(name).foreach(_.start())

  def stop(name: String): Unit =
    schedulerMap.get(name).foreach(_.stop())

  def getSchedulers(): Seq[SchedulerStatus] =
    schedulerMap.values.toSeq.map(_.status)

  private def updateProjectDependencies(): Future[Unit] =
    for {
      projectWithDependencies <- database
        .computeProjectDependencies()
        .mapFailure(e =>
          new Exception(
            s"Failed to compute project dependencies because of ${e.getMessage}"
          )
        )
      _ <- database.deleteDependenciesOfMovedProject()
      _ <- database
        .insertProjectDependencies(projectWithDependencies)
        .mapFailure(e =>
          new Exception(
            s"Failed to insert project dependencies because of ${e.getMessage}"
          )
        )

    } yield ()

  private def updateProjectCreationDate(): Future[Unit] = {
    // one request at time
    val future = for {
      oldestArtifacts <- database.computeAllProjectsCreationDates()
      _ <- oldestArtifacts.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
    } yield ()
    future.mapFailure(e => new Exception(s"not able to updateCreatedTimeIn all projects because of ${e.getMessage}"))
  }
}
