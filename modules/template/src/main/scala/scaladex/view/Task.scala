package scaladex.view

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import scaladex.core.util.TimeUtils

final case class Task(name: String, description: String)

object Task {
  val missingArtifacts: Task = Task(
    "missing-artifacts",
    "Fetch all missing artifacts from Maven Central, given a group ID and an optional artifact name."
  )

  val missingProjectNoArtifact: Task = Task(
    "missing-project-no-artifact",
    "Fetch a missing project without any published artifact, given a project reference."
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
