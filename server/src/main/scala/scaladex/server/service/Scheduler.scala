package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor
import akka.actor.ActorSystem
import akka.actor.Cancellable
import ch.epfl.scala.utils.TimerUtils
import scaladex.template.SchedulerStatus

class Scheduler(val name: String, runnable: Runnable)(implicit ec: ExecutionContext) {
  private var cancellable = Option.empty[Cancellable]
  private val system: ActorSystem = ActorSystem(name)
  private val scheduler: actor.Scheduler = system.scheduler
  private var _status: SchedulerStatus = SchedulerStatus.Created(Instant.now)

  def status: SchedulerStatus = _status

  def start(): Unit =
    status match {
      case s: SchedulerStatus.Started => ()
      case _ =>
        val can = scheduler.scheduleWithFixedDelay(0.minute, 10.minute) {
          _status = SchedulerStatus.Started(Instant.now, running = false, None, None)
          new Runnable {
            def run() = {
              val triggeredWhen = Instant.now
              _status = _status
                .asInstanceOf[SchedulerStatus.Started]
                .copy(running = true, triggeredWhen = Some(triggeredWhen))
              runnable.run()
              _status = _status
                .asInstanceOf[SchedulerStatus.Started]
                .copy(
                  running = false,
                  triggeredWhen = None,
                  durationOfLastRun = Some(
                    TimerUtils.toFiniteDuration(triggeredWhen, Instant.now)
                  )
                )
            }
          }
        }
        cancellable = Some(can)
    }

  def stop(): Unit =
    status match {
      case s: SchedulerStatus.Started =>
        cancellable.map(_.cancel())
        _status = SchedulerStatus.Stopped(Instant.now)
        cancellable = None
      case _ => ()
    }
}
