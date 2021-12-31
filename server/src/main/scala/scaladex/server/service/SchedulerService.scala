package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.github.GithubClient
import scaladex.view.SchedulerStatus

class SchedulerService(database: SchedulerDatabase, searchEngine: SearchEngine, githubClientOpt: Option[GithubClient])
    extends LazyLogging {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  private val schedulers = Seq(
    Scheduler("update-project-dependencies", updateProjectDependencies, 1.hour),
    Scheduler("update-project-creation-date", updateProjectCreationDate, 30.minutes),
    new SearchSynchronizer(database, searchEngine),
    new MoveReleasesSynchronizer(database)
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
            s"not able to getAllProjectDependencies because of ${e.getMessage}"
          )
        )
      _ <- database
        .insertProjectDependencies(projectWithDependencies)
        .mapFailure(e =>
          new Exception(
            s"not able to insertProjectDependencies because of ${e.getMessage}"
          )
        )

    } yield ()

  private def updateProjectCreationDate(): Future[Unit] = {
    // one request at time
    val future = for {
      oldestReleases <- database.computeAllProjectsCreationDate()
      _ <- oldestReleases.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
    } yield ()
    future.mapFailure(e => new Exception(s"not able to updateCreatedTimeIn all projects because of ${e.getMessage}"))
  }
}
