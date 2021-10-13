package scaladex.server.service

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.utils.ScalaExtensions._
import com.typesafe.scalalogging.LazyLogging

class SchedulerService(db: DatabaseApi) extends LazyLogging {
  import SchedulerService._
  implicit val ec: ExecutionContextExecutor =
    ExecutionContext.global // should be a fixed thread pool
  private val scaladex = new Scheduler("scaladex-scheduler", runnable)

  def start(name: String = scaladex.name): Unit =
    if (name == scaladex.name) scaladex.start() else ()

  def stop(name: String): Unit =
    if (name == scaladex.name) scaladex.stop() else ()

  def getScheduler(): Scheduler =
    scaladex

  private def runnable: Runnable = new Runnable {
    override def run(): Unit = {
      logger.info(s"Starting the scheduler ${Instant.now}")
      val future: Future[Unit] =
        try {
          for {
            _ <- updateProjectDependenciesTable(db)
          } yield logger.info(s"Finished running the scheduler ${Instant.now}")
        } catch {
          case NonFatal(e) =>
            Future.successful(
              logger.info(
                s"the scheduler ${scaladex.name} failed running because ${e.getMessage}"
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
      _ <- db
        .insertProjectWithDependentUponProjects(projectWithDependencies)
        .mapFailure(e =>
          new Exception(
            s"not able to insertProjectWithDependentUponProjects because of ${e.getMessage}"
          )
        )
    } yield ()
  }
}
