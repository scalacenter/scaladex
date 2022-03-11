package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import scaladex.view.AdminTaskStatus

abstract class AdminTask(val name: String, createdBy: String)(implicit ec: ExecutionContext) {
  private var _status: AdminTaskStatus = AdminTaskStatus.Created(name, createdBy, Instant.now())
  protected def run: Future[Unit]
  def getStatus: AdminTaskStatus = _status
  def start: Future[Unit] = {
    _status = _status.asInstanceOf[AdminTaskStatus.Created].start(Instant.now)
    run
      .map(_ => _status = _status.asInstanceOf[AdminTaskStatus.Started].success(Instant.now))
      .recover {
        case NonFatal(e) =>
          _status = _status.asInstanceOf[AdminTaskStatus.Started].failed(Instant.now, e.getMessage)
      }

  }
}

object AdminTask {
  def apply(name: String, createdBy: String, task: () => Future[Unit])(implicit ec: ExecutionContext): AdminTask =
    new AdminTask(name, createdBy) {
      override def run: Future[Unit] = task()
    }

}
