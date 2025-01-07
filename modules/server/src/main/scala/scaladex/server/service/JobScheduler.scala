package scaladex.server.service

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Cancellable
import scaladex.core.model.UserState
import scaladex.view.Job

class JobScheduler(val job: Job, run: () => Future[String])(using system: ActorSystem) extends LazyLogging:
  private given ExecutionContext = system.dispatcher
  private var cancellable = Option.empty[Cancellable]
  private var state: Job.State = Job.Stopped(Instant.now, None)
  private var results: Seq[Job.Result] = Seq.empty
  private var progress: Option[Job.Progress] = None

  def status: Job.Status = Job.Status(state, results, progress)

  def start(user: Option[UserState]): Unit =
    state match
      case _: Job.Started => ()
      case _ =>
        state = Job.Started(Instant.now, user.map(_.info.login))
        cancellable = Some(system.scheduler.scheduleWithFixedDelay(Duration.Zero, job.frequency)(runnable))

  def stop(user: Option[UserState]): Unit =
    state match
      case _: Job.Started =>
        cancellable.map(_.cancel())
        state = Job.Stopped(Instant.now, user.map(_.info.login))
        cancellable = None
      case _ => ()

  private def runnable: Runnable = new Runnable:
    override def run(): Unit =
      val start = Instant.now()
      val expectedDuration = results.headOption.map(_.duration)
      progress = Some(Job.Progress(start, expectedDuration))
      logger.info(s"Job ${job.name} starting")
      val result =
        try
          val message = Await.result(JobScheduler.this.run(), Duration.Inf)
          logger.info(s"Job ${job.name} succeeded: $message")
          val end = Instant.now()
          Job.Success(start, end, message)
        catch
          case NonFatal(cause) =>
            logger.error(s"Job ${job.name} failed", cause)
            val end = Instant.now()
            Job.Failure(start, end, cause)
      results = results :+ result
      progress = None
    end run
end JobScheduler
