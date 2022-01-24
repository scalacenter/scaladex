package scaladex.server.service

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor
import akka.actor.ActorSystem
import akka.actor.Cancellable
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.util.TimerUtils
import scaladex.view.SchedulerStatus

abstract class Scheduler(val name: String, frequency: FiniteDuration)(implicit ec: ExecutionContext)
    extends LazyLogging {
  private var cancellable = Option.empty[Cancellable]
  private val system: ActorSystem = ActorSystem(name)
  private val scheduler: actor.Scheduler = system.scheduler
  private var _status: SchedulerStatus = SchedulerStatus.Created(name, Instant.now)

  def run(): Future[Unit]
  def status: SchedulerStatus = _status

  def start(): Unit =
    status match {
      case s: SchedulerStatus.Started => ()
      case _ =>
        val can = scheduler.scheduleWithFixedDelay(0.minute, frequency) {
          _status = SchedulerStatus.Started(name, Instant.now, running = false, None, None)
          new Runnable {
            def run() = {
              val triggeredWhen = Instant.now
              _status = _status
                .asInstanceOf[SchedulerStatus.Started]
                .copy(lastRunAt = Some(triggeredWhen), running = true)
              runnable.run()
              _status = _status
                .asInstanceOf[SchedulerStatus.Started]
                .copy(
                  running = false,
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
      case _: SchedulerStatus.Started =>
        cancellable.map(_.cancel())
        _status = SchedulerStatus.Stopped(name, Instant.now)
        cancellable = None
      case _ => ()
    }

  private def runnable: Runnable = new Runnable {
    override def run(): Unit = {
      logger.info(s"Scheduler ${name}: Starting")
      try {
        Await.result(Scheduler.this.run(), Duration.Inf)
        logger.info(s"Scheduler $name: Finished")
      } catch {
        case NonFatal(e) =>
          logger.warn(s"Scheduler ${name}: Failed because ${e.getMessage}")
          logger.warn(e.getStackTrace.mkString("\n"))
      }
    }
  }
}

object Scheduler {
  def apply(name: String, job: () => Future[Unit], frequency: FiniteDuration)(
      implicit ec: ExecutionContext
  ): Scheduler =
    new Scheduler(name, frequency) {
      def run(): Future[Unit] = job()
    }
}
