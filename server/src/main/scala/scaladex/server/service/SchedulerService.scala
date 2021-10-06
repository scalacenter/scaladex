package scaladex.server.service

import java.time.Instant

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.utils.ScalaExtensions._
import com.typesafe.scalalogging.LazyLogging
import scaladex.template.SchedulerStatus

class SchedulerService(db: DatabaseApi) extends LazyLogging {
  import SchedulerService._
  implicit val ec: ExecutionContextExecutor =
    ExecutionContext.global // should be a fixed thread pool
  private val name = "scaladex-scheduler"
  private val schedulers = mutable.Map[String, Scheduler]()

  def start(name: String = name): Unit = {
    val updateScheduler: Scheduler = getScheduler(name) match {
      case Some(sc @ Scheduler(SchedulerStatus.Created(_, _), _)) =>
        sc.start(runnable)
      case Some(sc @ Scheduler(SchedulerStatus.Running(_, _), _)) => sc
      case Some(sc @ Scheduler(SchedulerStatus.Stopped(_, _), _)) =>
        sc.start(runnable)
      case None =>
        val newOne = Scheduler(SchedulerStatus.Created(name, Instant.now), None)
        newOne.start(runnable)
    }
    schedulers += (updateScheduler.name -> updateScheduler)
  }

  def stop(name: String = name): Unit = {
    val updateScheduler: Scheduler = getScheduler(name) match {
      case Some(sc @ Scheduler(SchedulerStatus.Created(_, _), _)) =>
        sc.stop()
      case Some(sc @ Scheduler(SchedulerStatus.Running(_, _), _)) =>
        sc.stop()
      case Some(sc @ Scheduler(SchedulerStatus.Stopped(_, _), _)) =>
        sc
      case None =>
        val newOne = Scheduler(SchedulerStatus.Created(name, Instant.now), None)
        newOne.stop() // maybe it's weird to that
    }
    schedulers += (updateScheduler.status.name -> updateScheduler)
  }

  def getScheduler(name: String = name): Option[Scheduler] =
    schedulers.get(name)

  // we should catch exceptions
  // right now if an exception is thrown it will kill the scheduler
  private def runnable: Runnable = new Runnable {
    override def run(): Unit = {
      logger.info(s"Starting the scheduler ${Instant.now}")
      val future: Future[Unit] =
        try {
          for {
            _ <- updateProjectDependenciesTable(db)
          } yield ()
        } catch {
          case NonFatal(e) =>
            Future.successful(
              logger.info(
                s"the scheduler $name failed running because ${e.getMessage}"
              )
            )
            Future.successful(logger.info(e.getStackTrace.mkString("\n")))
        }
      Await.result(future, Duration.Inf)
    }
  }

}

object SchedulerService {
  def updateProjectDependenciesTable(
      db: DatabaseApi
  )(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      projectWithDependencies <- db
        .getAllProjectDependencies()
        .mapFailure(e =>
          new Exception(
            s"not able to getAllProjectDependencies because of ${e.getMessage}"
          )
        )
      _ <- projectWithDependencies
        .map { case (source, target) =>
          db.insertProjectWithDependentUponProjects(source, target)
        }
        .sequence
        .mapFailure(e =>
          new Exception(
            s"not able to insertProjectWithDependentUponProjects because of ${e.getMessage}"
          )
        )
    } yield ()
  }
}
