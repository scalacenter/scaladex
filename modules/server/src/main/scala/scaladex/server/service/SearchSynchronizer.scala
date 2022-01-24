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

class SearchSynchronizer(database: SchedulerDatabase, searchEngine: SearchEngine)(implicit ec: ExecutionContext)
    extends Scheduler("sync-search", 30.minutes)
    with LazyLogging {
  override def run(): Future[Unit] =
    for {
      allProjects <- database.getAllProjects()
      allProjectsAndStatus = allProjects.map(p => (p, p.githubStatus))
      deprecatedProjects = allProjects.filter(_.settings.deprecated).map(_.reference).toSet

      // Create a map of project reference to their old references
      movedProjects = allProjectsAndStatus
        .collect {
          case (p, GithubStatus.Moved(_, newRef)) =>
            newRef -> p.reference
        }
        .groupMap { case (newRef, ref) => newRef } { case (newRef, ref) => ref }
      projectsToDelete = deprecatedProjects ++
        allProjectsAndStatus.collect {
          case (p, GithubStatus.NotFound(_) | GithubStatus.Failed(_, _, _)) => p.reference
        }
      projectsToSync = allProjectsAndStatus
        .collect {
          case (p, GithubStatus.Ok(_) | GithubStatus.Unknown(_)) if !deprecatedProjects.contains(p.reference) => p
        }

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
      artifacts <- database.getArtifacts(project.reference)
      inverseProjectDependencies <- database.countInverseProjectDependencies(project.reference)
      document = ProjectDocument(project, artifacts, inverseProjectDependencies, formerReferences)
      _ <- searchEngine.insert(document)
      _ <- formerReferences.mapSync(searchEngine.delete)
    } yield ()
}
