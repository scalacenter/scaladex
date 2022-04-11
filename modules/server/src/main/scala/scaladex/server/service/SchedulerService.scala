package scaladex.server.service
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Env
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.GithubClient
import scaladex.view.SchedulerStatus
import scaladex.infra.SqlDatabase

class SchedulerService(
    env: Env,
    database: SqlDatabase,
    searchEngine: SearchEngine,
    githubClientOpt: Option[GithubClient],
    sonatypeSynchronizer: SonatypeSynchronizer,
    userSessionSynchronizer: UserSessionSynchronizer
) extends LazyLogging {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  val searchSynchronizer = new SearchSynchronizer(database, searchEngine)
  val projectDependenciesUpdater = new DependencyUpdater(database)

  private val schedulers = Seq(
    Scheduler("update-dependency-tables", projectDependenciesUpdater.updateAll, 1.hour),
    Scheduler("update-project-creation-date", updateProjectCreationDate, 30.minutes),
    Scheduler("sync-search", searchSynchronizer.syncAll, 30.minutes),
    new MovedArtifactsSynchronizer(database),
    userSessionSynchronizer,
    new MovedArtifactsSynchronizer(database),
    Scheduler("metrics", (new Metrics(database)).run, 1.hour),
  ) ++
    Option.when(!env.isLocal)(Scheduler("sync-sonatype-missing-releases", sonatypeSynchronizer.syncAll, 24.hours)) ++
    githubClientOpt.map(client => new GithubUpdater(database, client))

  private val schedulerMap = schedulers.map(s => s.name -> s).toMap

  def startAll(): Unit =
    schedulerMap.values.foreach(_.start())

  def start(name: String): Unit =
    schedulerMap.get(name).foreach(_.start())

  def stop(name: String): Unit =
    schedulerMap.get(name).foreach(_.stop())

  def getSchedulers(): Seq[SchedulerStatus] =
    schedulerMap.values.toSeq.map(_.status)

  private def updateProjectCreationDate(): Future[Unit] = {
    // one request at time
    val future = for {
      oldestArtifacts <- database.computeAllProjectsCreationDates()
      _ <- oldestArtifacts.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
    } yield ()
    future.mapFailure(e => new Exception(s"not able to updateCreatedTimeIn all projects because of ${e.getMessage}"))
  }
}
