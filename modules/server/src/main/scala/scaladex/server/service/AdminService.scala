package scaladex.server.service
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.Env
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.Project.Settings
import scaladex.core.model.UserState
import scaladex.core.service.GithubClient
import scaladex.core.service.ProjectService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions.*
import scaladex.view.Job
import scaladex.view.Task

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem

class AdminService(
    env: Env,
    database: SchedulerDatabase,
    searchEngine: SearchEngine,
    githubClientOpt: Option[GithubClient],
    mavenCentralService: MavenCentralService
)(using system: ActorSystem)
    extends LazyLogging:
  private given ExecutionContext = system.dispatcher

  val projectService = new ProjectService(database, searchEngine)
  val searchSynchronizer = new SearchSynchronizer(database, projectService, searchEngine)
  val projectDependenciesUpdater = new DependencyUpdater(database, projectService)
  val userSessionService = new UserSessionService(database)
  val artifactService = new ArtifactService(database)
  val githubUpdaterOpt: Option[GithubUpdater] = githubClientOpt.map(client => new GithubUpdater(database, client))

  private val jobs: Map[String, JobScheduler] =
    val seq = Seq(
      new JobScheduler(Job.syncSearch, searchSynchronizer.syncAll),
      new JobScheduler(Job.projectDependencies, projectDependenciesUpdater.updateAll),
      new JobScheduler(Job.projectCreationDates, updateProjectCreationDate),
      new JobScheduler(Job.moveArtifacts, artifactService.moveAll),
      new JobScheduler(Job.userSessions, userSessionService.updateAll),
      new JobScheduler(Job.latestArtifacts, artifactService.updateAllLatestVersions)
    ) ++
      githubClientOpt.map { client =>
        val githubUpdater = new GithubUpdater(database, client)
        new JobScheduler(Job.githubInfo, githubUpdater.updateAll)
      } ++ (
        if !env.isLocal then
          Seq(
            new JobScheduler(Job.missingMavenArtifacts, mavenCentralService.findMissing),
            new JobScheduler(Job.nonStandardArtifacts, mavenCentralService.findNonStandard)
          )
        else Seq.empty
      )
    seq.map(s => s.job.name -> s).toMap
  end jobs

  private var tasks = Seq.empty[TaskRunner]

  def startAllJobs(): Unit =
    jobs.values.foreach(_.start(None))

  def startJob(name: String, user: UserState): Unit =
    jobs.get(name).foreach(_.start(Some(user)))

  def stopJob(name: String, user: UserState): Unit =
    jobs.get(name).foreach(_.stop(Some(user)))

  def allJobStatuses: Seq[(Job, Job.Status)] =
    jobs.values.map(s => s.job -> s.status).toSeq

  def findMissingArtifacts(
      groupId: Artifact.GroupId,
      artifactNameOpt: Option[Artifact.Name],
      user: UserState
  ): Unit =
    val input = Seq("Group ID" -> groupId.value) ++
      artifactNameOpt.map(name => "Artifact Name" -> name.value)
    val task = TaskRunner.run(Task.findMissingArtifacts, user.info.login, input) { () =>
      mavenCentralService.syncOne(groupId, artifactNameOpt)
    }
    tasks = tasks :+ task
  end findMissingArtifacts

  def allTaskStatuses: Seq[Task.Status] = tasks.map(_.status)

  def addEmptyProject(reference: Project.Reference, user: UserState): Unit =

    val input = Seq("Organization" -> reference.organization.value, "Repository" -> reference.repository.value)

    val task = TaskRunner.run(Task.addEmptyProject, user.info.login, input) { () =>
      githubClientOpt.fold(throw new Exception("No configured Github token")) { githubClient =>
        githubClient.getProjectInfo(reference).flatMap {
          case GithubResponse.Failed(code, errorMessage) =>
            throw new Exception(s"Failed to add project due to GitHub error $code : $errorMessage")
          case GithubResponse.MovedPermanently(res) =>
            throw new Exception(s"Failed to add project. Project moved to ${res._1.repository}")
          case GithubResponse.Ok((_, info)) =>
            info.scalaPercentage.fold {
              throw new Exception(s"Failed to add project. Could not obtain percentage of Scala for this project.")
            } { percentage =>
              if percentage <= 0 then throw new Exception(s"Failed to add project. Project seems not a Scala one.")
              else
                val project =
                  new Project(
                    organization = reference.organization,
                    repository = reference.repository,
                    creationDate = None,
                    GithubStatus.Ok(java.time.Instant.now),
                    githubInfo = Some(info),
                    settings = Settings.empty
                  )
                database
                  .insertProject(project)
                  .flatMap(_ => searchSynchronizer.syncProject(reference))
                  .map(_ => "success")
            }
        }
      }
    }
    tasks = tasks :+ task
  end addEmptyProject

  def updateGithubInfo(reference: Project.Reference, user: UserState): Unit =
    val input = Seq("Organization" -> reference.organization.value, "Repository" -> reference.repository.value)

    val task = TaskRunner.run(Task.updateGithubInfo, user.info.login, input) { () =>
      githubUpdaterOpt.fold(throw new Exception("No configured Github token")) { githubUpdater =>
        githubUpdater.update(reference).map(_.toString)
      }
    }
    tasks = tasks :+ task

  def republishArtifacts(user: UserState): Unit =
    val task = TaskRunner.run(Task.republishArtifacts, user.info.login, input = Seq.empty) { () =>
      mavenCentralService.republishArtifacts()
    }
    tasks = tasks :+ task

  private def updateProjectCreationDate(): Future[String] =
    for
      creationDates <- database.computeProjectsCreationDates()
      _ <- creationDates.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
    yield s"Updated ${creationDates.size} creation dates"
end AdminService
