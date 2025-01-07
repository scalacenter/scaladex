package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.search.ProjectDocument
import scaladex.core.service.ProjectService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions.*

class SearchSynchronizer(database: SchedulerDatabase, service: ProjectService, searchEngine: SearchEngine)(
    using ExecutionContext
) extends LazyLogging:
  def syncAll(): Future[String] =
    for
      allProjects <- database.getAllProjects()
      allProjectsAndStatus = allProjects.map(p => (p, p.githubStatus))

      // Create a map of project reference to their old references
      movedProjects = allProjectsAndStatus
        .collect { case (p, GithubStatus.Moved(_, newRef)) => newRef -> p.reference }
        .groupMap { case (newRef, _) => newRef } { case (_, ref) => ref }
      projectsToDelete =
        allProjectsAndStatus.collect { case (p, GithubStatus.NotFound(_)) => p.reference }
      projectsToSync = allProjectsAndStatus
        .collect { case (p, status) if status.isOk || status.isUnknown || status.isFailed => p }

      _ = logger.info(s"${movedProjects.size} projects were moved")
      _ = logger.info(s"Deleting ${projectsToDelete.size} projects from search engine")
      _ = logger.info(s"Syncing ${projectsToSync.size} projects in search engine")

      _ <- projectsToDelete.mapSync(searchEngine.delete)
      _ <- projectsToSync.mapSync { project =>
        val formerReferences = movedProjects.getOrElse(project.reference, Seq.empty)
        insertDocument(project, formerReferences)
      }
    yield s"Updated ${projectsToSync.size} projects and removed ${projectsToDelete.size} projects"

  def syncProject(ref: Project.Reference): Future[Unit] =
    for
      projectOpt <- database.getProject(ref)
      formerReferences <- database.getFormerReferences(ref)
      _ <- projectOpt match
        case Some(project) => insertDocument(project, formerReferences)
        case None =>
          logger.error(s"Cannot update project document of $ref because: project not found")
          Future.successful(())
    yield ()

  private def insertDocument(project: Project, formerReferences: Seq[Project.Reference]): Future[Unit] =
    for
      header <- service.getHeader(project)
      dependents <- database.countProjectDependents(project.reference)
      document = ProjectDocument(project, header, dependents, formerReferences)
      _ <- searchEngine.insert(document)
      _ <- formerReferences.mapSync(searchEngine.delete)
    yield ()
end SearchSynchronizer
