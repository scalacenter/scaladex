package scaladex.server.service

import java.time.Instant

import scaladex.view.Task

import cats.effect.IO

class TaskRunner private (val task: Task, user: String, input: Seq[(String, String)], run: () => IO[String]):
  private val start = Instant.now()
  private var state: Task.State = Task.Running(start)

  def status: Task.Status = Task.Status(task.name, user, start, input, state)

  run().unsafeRunAsync {
    case Right(message) =>
      state = Task.Success(start, Instant.now(), message)
    case Left(cause) =>
      state = Task.Failure(start, Instant.now(), cause)
  }
end TaskRunner

object TaskRunner:
  def run(task: Task, user: String, input: Seq[(String, String)])(run: () => IO[String]) =
    new TaskRunner(task, user, input, run)
