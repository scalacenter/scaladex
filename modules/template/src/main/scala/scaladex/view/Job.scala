package scaladex.view

import java.time.Instant

import scala.concurrent.duration.*

import scaladex.core.util.TimeUtils

case class Job(name: String, description: String, frequency: FiniteDuration)

object Job:
  val syncSearch: Job = Job(
    "sync-search",
    "Synchronize the search engine with the database.",
    30.minutes
  )
  val githubInfo: Job = Job(
    "github-info",
    "Update the Github information of all projects.",
    1.hour
  )
  val projectDependencies: Job = Job(
    "project-dependencies",
    "Compute the dependencies of all projects, based on their latest versions, and store the result in the project_dependencies table.",
    1.hour
  )
  val projectCreationDates: Job = Job(
    "project-creation-dates",
    "Update the creation date of all projects based on their earliest released artifact.",
    30.minutes
  )
  val moveArtifacts: Job = Job(
    "move-artifacts",
    "Rename the project reference of the artifacts, if the project has been renamed in Github.",
    30.minutes
  )
  val userSessions: Job = Job(
    "user-sessions",
    "Update the list of organizations and repositories of all user sessions.",
    1.hour
  )
  val missingMavenArtifacts: Job = Job(
    "missing-maven-artifacts",
    "Find missing artifacts in Maven Central of the known group IDs.",
    24.hours
  )
  val nonStandardArtifacts: Job = Job(
    "non-standard-artifacts",
    "Find missing non-standard artifacts from Maven Central",
    2.hours
  )
  val latestArtifacts: Job = Job(
    "latest-artifacts",
    "Update latest version of artifacts",
    24.hours
  )

  case class Status(state: State, results: Seq[Result], progress: Option[Progress]):
    def isStarted: Boolean = state.isInstanceOf[Started]

  sealed trait State:
    def when: Instant
    def fromNow: FiniteDuration = TimeUtils.toFiniteDuration(when, Instant.now())
  case class Stopped(when: Instant, user: Option[String]) extends State
  case class Started(when: Instant, user: Option[String]) extends State

  sealed trait Result:
    def start: Instant
    def end: Instant
    def duration: FiniteDuration = TimeUtils.toFiniteDuration(start, end)
    def fromNow: FiniteDuration = TimeUtils.toFiniteDuration(end, Instant.now())
  case class Success(start: Instant, end: Instant, message: String) extends Result
  case class Failure(start: Instant, end: Instant, cause: Throwable) extends Result

  case class Progress(start: Instant, expectedDuration: Option[FiniteDuration]):
    def duration: FiniteDuration = TimeUtils.toFiniteDuration(start, Instant.now())
    def percentage: Double = expectedDuration.map(TimeUtils.percentage(duration, _)).getOrElse(100d)
end Job
