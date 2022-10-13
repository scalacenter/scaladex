package scaladex.server.service
import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging

import scaladex.core.model.{Artifact, Env, GithubInfo, GithubResponse, GithubStatus, Project, UserState}
import scaladex.core.model.Project.{Settings}

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

    val input = Seq("Organization" -> reference.organization.value, "Repository" -> reference.repository.value)

    def buildProject(info: GithubInfo): Project = new Project(
      organization = reference.organization,
      repository = reference.repository,
      creationDate = None,
      GithubStatus.Ok(java.time.Instant.now),
      githubInfo = Some(info),
      settings = Settings.default
    )

    val task = TaskRunner.run(Task.missingProjectNoArtifact, user.info.login, input) { () =>
      githubClientOpt.fold(throw new Exception("Failed because 1 ??")) { githubClient =>
        githubClient.getProjectInfo(reference).flatMap {
          case GithubResponse.Failed(code, errorMessage) =>
              throw new Exception(s"Failed to add project due to GitHub error $code : $errorMessage")
          case GithubResponse.MovedPermanently(res) =>
            throw new Exception(s"Failed to add project. Project moved to ${res._1.repository}")
          case GithubResponse.Ok((_, info)) =>
            info.scalaPercentage.fold(throw new Exception("Failed because 2 ???")) { percentage =>
                if(percentage <= 0) {
                  throw new Exception(s"Failed to add project. Project seems not a Scala one.")
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
