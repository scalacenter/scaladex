package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import scaladex.core.util.ScalaExtensions.*
import scaladex.view.Task

class TaskRunner private (val task: Task, user: String, input: Seq[(String, String)], run: () => Future[String])(
    using ExecutionContext
):
  private val start = Instant.now()
  private var state: Task.State = Task.Running(start)

  def status: Task.Status = Task.Status(task.name, user, start, input, state)

  run().failWithTry.foreach {
    case Success(message) =>
      state = Task.Success(start, Instant.now(), message)
    case Failure(cause) =>
      state = Task.Failure(start, Instant.now(), cause)
  }
end TaskRunner

object TaskRunner:
  def run(task: Task, user: String, input: Seq[(String, String)])(run: () => Future[String])(
      using ExecutionContext
  ) =
    new TaskRunner(task, user, input, run)
