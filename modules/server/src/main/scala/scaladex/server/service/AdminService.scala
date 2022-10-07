package scaladex.server.service
import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging

import scaladex.core.model.{Artifact, Env, GithubInfo, GithubResponse, GithubStatus, Project, UserState}
import scaladex.core.model.Project.{Organization, Repository, Settings}

import scaladex.core.service.GithubClient
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._
import scaladex.view.Job
import scaladex.view.Task

class AdminService(
    env: Env,
    database: SchedulerDatabase,
    searchEngine: SearchEngine,
    githubClientOpt: Option[GithubClient],
    sonatypeSynchronizer: SonatypeService
)(implicit actorSystem: ActorSystem)
    extends LazyLogging {
  import actorSystem.dispatcher

  val searchSynchronizer = new SearchSynchronizer(database, searchEngine)
  val projectDependenciesUpdater = new DependencyUpdater(database)
  val userSessionService = new UserSessionService(database)
  val artifactsService = new ArtifactsService(database)

  private val jobs: Map[String, JobScheduler] = {
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

  private var tasks = Seq.empty[TaskRunner]

  def startAllJobs(): Unit =
    jobs.values.foreach(_.start(None))

  def startJob(name: String, user: UserState): Unit =
    jobs.get(name).foreach(_.start(Some(user)))

  def stopJob(name: String, user: UserState): Unit =
    jobs.get(name).foreach(_.stop(Some(user)))

  def allJobStatuses: Seq[(Job, Job.Status)] =
    jobs.values.map(s => s.job -> s.status).toSeq

  def runMissingArtifactsTask(
      groupId: Artifact.GroupId,
      artifactNameOpt: Option[Artifact.Name],
      user: UserState
  ): Unit = {
    val input = Seq("Group ID" -> groupId.value) ++
      artifactNameOpt.map(name => "Artifact Name" -> name.value)
    val task = TaskRunner.run(Task.missingArtifacts, user.info.login, input) { () =>
      sonatypeSynchronizer.syncOne(groupId, artifactNameOpt)
    }
    tasks = tasks :+ task
  }

  def allTaskStatuses: Seq[Task.Status] = tasks.map(_.status)

  def addProjectNoArtifact(reference: Project.Reference, user: UserState
                          ): Unit = {

    /**
     * Builds empty Settings
     * @return
     */
    def buildSettings(): Settings = Settings(
      false, None, false, None, List.empty, false,false, Set.empty, Set.empty, None, None)

    /**
     * Givem a GithubInfo, creates a scaladex Project
     * @param info
     * @return
     */
    def buildProject(info: GithubInfo): Project = new Project(
      organization = Organization(""), // FIXME
      repository = Repository(""), // FIXME
      creationDate = None, // FIXME
      GithubStatus.Ok(java.time.Instant.now),
      githubInfo = Some(info),
      settings = buildSettings()
    )

    val input = Seq("Organization" -> reference.organization.value, "Repository" -> reference.repository.value)

    val task = TaskRunner.run(Task.missingProjectNoArtifact, user.info.login, input) { () =>
      githubClientOpt match {
        case None =>
          throw new Exception("Failed because 1 ??")
        case Some(githubClient) =>
          githubClient.getProjectInfo(reference).flatMap {
            case GithubResponse.Ok((_, info)) =>
              info.scalaPercentage match {
                case None =>
                  throw new Exception("Failed because 2 ???")
                case Some(percentage) =>
                  if(percentage <= 0) {
                    throw new Exception("Failed because 3 ???")
                  } else {
                    val project = buildProject(info)
                    database.insertProject(project)
                      .flatMap(_ => searchSynchronizer.syncProject(reference))
                      .map(_ => "success")
                  }
              }
          }
      }
    }
    tasks = tasks :+ task
  }

  private def updateProjectCreationDate(): Future[String] =
    for {
      creationDates <- database.computeAllProjectsCreationDates()
      _ <- creationDates.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
    } yield s"Updated ${creationDates.size} creation dates"
}
