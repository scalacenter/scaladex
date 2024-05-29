package scaladex.core.service

import scaladex.core.model._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class ProjectService(database: WebDatabase)(implicit context: ExecutionContext) {
  def getProjectHeader(project: Project): Future[Option[ProjectHeader]] = {
    val ref = project.reference
    for {
      latestArtifacts <- database.getLatestArtifacts(ref, project.settings.preferStableVersion)
      versionCount <- database.countVersions(ref)
    } yield ProjectHeader(
      project.reference,
      latestArtifacts,
      versionCount,
      project.settings.defaultArtifact,
      project.settings.preferStableVersion
    )
  }
}
