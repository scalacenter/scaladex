package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration._

import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.utils.ScalaExtensions._
import com.typesafe.scalalogging.LazyLogging
import scaladex.server.service.SchedulerService._
import scaladex.template.SchedulerStatus

class SchedulerService(db: SchedulerDatabase) extends LazyLogging {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  private val mostDependentProjectScheduler = new Scheduler("most-dependent", mostDependentProjectJob, 1.hour)
  private val updateProject = new Scheduler("update-projects", updateProjectJob, 30.minutes)

  private val schedulers = Map[String, Scheduler](
    mostDependentProjectScheduler.name -> mostDependentProjectScheduler,
    updateProject.name -> updateProject
  )

  def startAll(): Unit =
    schedulers.values.foreach(_.start())

  def start(name: String): Unit =
    schedulers.get(name).foreach(_.start())

  def stop(name: String): Unit =
    schedulers.get(name).foreach(_.stop())

  def getSchedulers(): Seq[SchedulerStatus] =
    schedulers.values.toSeq.map(_.status)

  private def mostDependentProjectJob(): Future[Unit] =
    for {
      _ <- updateProjectDependenciesTable(db)
    } yield ()

  private def updateProjectJob(): Future[Unit] =
    for {
      _ <- updateCreatedTimeIn(db)
    } yield ()
}

object SchedulerService {

  def updateProjectDependenciesTable(db: SchedulerDatabase)(implicit ec: ExecutionContext): Future[Unit] =
    for {
      projectWithDependencies <- db
        .getAllProjectDependencies()
        .mapFailure(e =>
          new Exception(
            s"not able to getAllProjectDependencies because of ${e.getMessage}"
          )
        )
      _ <- db
        .insertProjectDependencies(projectWithDependencies)
        .mapFailure(e =>
          new Exception(
            s"not able to insertProjectDependencies because of ${e.getMessage}"
          )
        )

    } yield ()

  private def updateCreatedTimeIn(db: SchedulerDatabase)(implicit ec: ExecutionContext): Future[Unit] =
    db.updateCreatedInProjects()
      .mapFailure(e =>
        new Exception(
          s"not able to updateCreatedTimeIn all projects because of ${e.getMessage}"
        )
      )
}
