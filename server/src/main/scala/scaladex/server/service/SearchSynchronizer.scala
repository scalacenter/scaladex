package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.search.ProjectDocument
import ch.epfl.scala.services.SchedulerDatabase
import ch.epfl.scala.services.SearchEngine
import ch.epfl.scala.utils.ScalaExtensions._
import com.typesafe.scalalogging.LazyLogging

class SearchSynchronizer(db: SchedulerDatabase, searchEngine: SearchEngine)(implicit ec: ExecutionContext)
    extends Scheduler("search-synchronizer", 30.minutes)
    with LazyLogging {
  override def run(): Future[Unit] =
    for {
      allProjects <- db.getAllProjects()
      allProjectsAndStatus = allProjects.map(p => (p, p.githubStatus))

      // Create a map of project reference to their old references
      movedProjects = allProjectsAndStatus
        .collect {
          case (p, GithubStatus.Moved(_, newRef)) =>
            newRef -> Project.Reference(p.organization, p.repository)
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
