package scaladex.view

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import scaladex.core.util.TimeUtils

final case class Task(name: String, description: String)

object Task {
  val findMissingArtifacts: Task = Task(
    "find-missing-artifacts",
    "Find all missing artifacts from Maven Central."
  )

  val addEmptyProject: Task = Task(
    "add-empty-project",
    "Add project from Github without any published artifact."
  )

  val updateGithubInfo: Task = Task(
    "update-github-info",
    "Update the Github info of an existing project"
  )

  case class Status(name: String, user: String, start: Instant, input: Seq[(String, String)], state: State) {
    def fromNow: FiniteDuration = TimeUtils.toFiniteDuration(start, Instant.now())
  }

  sealed trait State
  case class Running(start: Instant) extends State {
    def fromNow: FiniteDuration = TimeUtils.toFiniteDuration(start, Instant.now())
  }

  case class Success(start: Instant, end: Instant, message: String) extends State {
    def duration: FiniteDuration = TimeUtils.toFiniteDuration(start, end)
  }

  case class Failure(start: Instant, end: Instant, cause: Throwable) extends State {
    def duration: FiniteDuration = TimeUtils.toFiniteDuration(start, end)
  }
}
