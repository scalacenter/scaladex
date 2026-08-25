package scaladex.server.service

import scala.concurrent.ExecutionContext

import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.search.ProjectDocument
import scaladex.core.service.ProjectService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.util.ScalaExtensions.*

import cats.effect.ContextShift
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging

class SearchSynchronizer(database: SchedulerDatabase, service: ProjectService, searchEngine: SearchEngine)(
    using ExecutionContext
) extends LazyLogging:
  private given ContextShift[IO] = IO.contextShift(summon[ExecutionContext])

  def syncAll(): IO[String] =
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

      _ <- projectsToDelete.mapIO(ref => searchEngine.delete(ref).toIO)
      _ <- projectsToSync.mapIO { project =>
        val formerReferences = movedProjects.getOrElse(project.reference, Seq.empty)
        insertDocument(project, formerReferences)
      }
    yield s"Updated ${projectsToSync.size} projects and removed ${projectsToDelete.size} projects"

  def syncProject(ref: Project.Reference): IO[Unit] =
    for
      projectOpt <- database.getProject(ref)
      formerReferences <- database.getFormerReferences(ref)
      _ <- projectOpt match
        case Some(project) => insertDocument(project, formerReferences)
        case None =>
          logger.error(s"Cannot update project document of $ref because: project not found")
          IO.unit
    yield ()

  private def insertDocument(project: Project, formerReferences: Seq[Project.Reference]): IO[Unit] =
    for
      header <- service.getHeader(project)
      dependents <- database.countProjectDependents(project.reference)
      document = ProjectDocument(project, header, dependents, formerReferences)
      _ <- searchEngine.insert(document).toIO
      _ <- formerReferences.mapIO(ref => searchEngine.delete(ref).toIO)
    yield ()
end SearchSynchronizer
