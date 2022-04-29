package scaladex.server.service
import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.core.service.GithubClient
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.server.service.JobScheduler
import scaladex.view.Job

class JobService(
    env: Env,
    database: SchedulerDatabase,
    searchEngine: SearchEngine,
    githubClientOpt: Option[GithubClient],
    sonatypeSynchronizer: SonatypeSynchronizer
)(implicit actorSystem: ActorSystem)
    extends LazyLogging {
  import actorSystem.dispatcher

  val searchSynchronizer = new SearchSynchronizer(database, searchEngine)
  val projectDependenciesUpdater = new DependencyUpdater(database)
  val userSessionService = new UserSessionService(database)
  val artifactsService = new ArtifactsService(database)

  private val schedulers: Map[String, JobScheduler] = {
    val seq = Seq(
      new JobScheduler(Job.syncSearch, searchSynchronizer.syncAll),
      new JobScheduler(Job.projectDependencies, projectDependenciesUpdater.updateAll),
      new JobScheduler(Job.projectCreationDates, updateProjectCreationDate),
      new JobScheduler(Job.moveArtifacts, artifactsService.moveAll),
      new JobScheduler(Job.userSessions, userSessionService.updateAll)
    ) ++
      githubClientOpt.map { client =>
        val githubUpdater = new GithubUpdater(database, client)
        new JobScheduler(Job.githubInfo, githubUpdater.updateAll)
      } ++
      Option.when(!env.isLocal)(new JobScheduler(Job.missingMavenArtifacts, sonatypeSynchronizer.syncAll))
    seq.map(s => s.job.name -> s).toMap
  }

  def startAll(): Unit =
    schedulers.values.foreach(_.start(None))

  def start(name: String, user: UserState): Unit =
    schedulers.get(name).foreach(_.start(Some(user)))

  def stop(name: String, user: UserState): Unit =
    schedulers.get(name).foreach(_.stop(Some(user)))

  def allStatuses: Seq[(Job, Job.Status)] =
    schedulers.values.map(s => s.job -> s.status).toSeq

  private def updateProjectCreationDate(): Future[String] =
    for {
      creationDates <- database.computeAllProjectsCreationDates()
      _ <- creationDates.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
    } yield s"Updated ${creationDates.size} creation dates"
}
