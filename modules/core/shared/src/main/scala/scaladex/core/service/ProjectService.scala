package scaladex.core.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model._

class ProjectService(database: WebDatabase)(implicit context: ExecutionContext) {
  def getProjectHeader(project: Project): Future[Option[ProjectHeader]] = {
    val ref = project.reference
    for {
      latestArtifacts <- database.getLatestArtifacts(ref)
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
