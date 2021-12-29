package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.search.ProjectDocument
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions._

class SearchSynchronizer(db: SchedulerDatabase, searchEngine: SearchEngine)(implicit ec: ExecutionContext)
    extends Scheduler("sync-search", 30.minutes)
    with LazyLogging {
  override def run(): Future[Unit] =
    for {
      allProjects <- db.getAllProjects()
      allProjectsAndStatus = allProjects.map(p => (p, p.githubStatus))

      // Create a map of project reference to their old references
      movedProjects = allProjectsAndStatus
        .collect {
          case (p, GithubStatus.Moved(_, newRef)) =>
            newRef -> p.reference
        }
        .groupMap { case (newRef, ref) => newRef } { case (newRef, ref) => ref }
      projectsToDelete = allProjectsAndStatus.collect {
        case (p, GithubStatus.NotFound(_) | GithubStatus.Failed(_, _, _)) => p.reference
      }
      projectsToSync = allProjectsAndStatus.collect { case (p, GithubStatus.Ok(_) | GithubStatus.Unknown(_)) => p }

      _ = logger.info(s"${movedProjects.size} projects were moved")
      _ = logger.info(s"Deleting ${projectsToDelete.size} projects from search engine")
      _ = logger.info(s"Syncing ${projectsToSync.size} projects in search engine")

      _ <- projectsToDelete.mapSync(searchEngine.delete)
      _ <- projectsToSync.mapSync { project =>
        val formerReferences = movedProjects.getOrElse(project.reference, Seq.empty)
        insertDocument(project, formerReferences)
      }
    } yield ()

  private def insertDocument(project: Project, formerReferences: Seq[Project.Reference]): Future[Unit] =
    for {
      releases <- db.findReleases(project.reference)
      inverseProjectDependencies <- db.countInverseProjectDependencies(project.reference)
      document = ProjectDocument(project, releases, inverseProjectDependencies, formerReferences)
      _ <- searchEngine.insert(document)
      _ <- formerReferences.mapSync(searchEngine.delete)
    } yield ()
}
