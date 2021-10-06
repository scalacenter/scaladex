package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor
import akka.actor.ActorSystem
import akka.actor.Cancellable
import scaladex.template.SchedulerStatus

final case class Scheduler(
//    name: String,
    status: SchedulerStatus,
    cancellable: Option[Cancellable]
)(implicit ec: ExecutionContext) {
  val name = status.name
  private val system: ActorSystem = ActorSystem(name)
  val scheduler: actor.Scheduler = system.scheduler

  private def run(run: Runnable): Cancellable =
    scheduler.scheduleWithFixedDelay(0.minute, 10.minutes)(run)

  def start(runnable: Runnable): Scheduler = {
    val cancellable = run(runnable)
    this.copy(
      status = SchedulerStatus.Running(status.name, Instant.now),
      cancellable = Some(cancellable)
    )
  }

  def stop(): Scheduler = {
    cancellable.map(_.cancel())
    this.copy(
      status = SchedulerStatus.Stopped(status.name, Instant.now()),
      cancellable = None
    )
  }

}
