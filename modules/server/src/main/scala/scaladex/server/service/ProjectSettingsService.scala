package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.Project
import scaladex.core.service.ProjectService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine

// Shared by the web settings form and the REST API.
class ProjectSettingsService(
    database: SchedulerDatabase,
    projectService: ProjectService,
    artifactService: ArtifactService,
    searchEngine: SearchEngine
)(using ExecutionContext):
  private val searchSynchronizer = new SearchSynchronizer(database, projectService, searchEngine)

  // No optimistic locking: a caller doing read-modify-write (the REST PATCH) can lose a concurrent update.
  def updateSettings(ref: Project.Reference, settings: Project.Settings): Future[Unit] =
    for
      _ <- database.updateProjectSettings(ref, settings)
      _ <- artifactService.updateLatestVersions(ref, settings.preferStableVersion)
      _ <- searchSynchronizer.syncProject(ref)
    yield ()
end ProjectSettingsService
