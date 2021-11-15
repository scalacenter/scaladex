package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.search.ProjectDocument
import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.services.SearchEngine
import com.typesafe.scalalogging.LazyLogging

class SearchSynchronizer(db: SchedulerDatabase, searchEngine: SearchEngine)(implicit ec: ExecutionContext)
    extends LazyLogging {
  def run(): Future[Unit] =
    db.getAllProjects().flatMap { projects =>
      logger.info(s"Syncing search of ${projects.size} projects")
      projects.foldLeft(Future.successful(())) {
        case (future, project) =>
          for {
            _ <- future
            document <- getDocument(project)
            _ <- searchEngine.insert(document)
          } yield ()
      }
    }

  private def getDocument(project: NewProject): Future[ProjectDocument] =
    for {
      releases <- db.findReleases(project.reference)
      inverseProjectDependencies <- db.countInverseProjectDependencies(project.reference)
    } yield ProjectDocument(project, releases, inverseProjectDependencies)
}
