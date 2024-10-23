package scaladex.core.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model._
import scaladex.core.model.search.SearchParams

class ProjectService(database: WebDatabase, searchEngine: SearchEngine)(implicit context: ExecutionContext) {
  def getProjects(languages: Seq[Language], platforms: Seq[Platform]): Future[Seq[Project.Reference]] = {
    val searchParams = SearchParams(languages = languages, platforms = platforms)
    searchEngine.findRefs(searchParams)
  }

  def getProject(ref: Project.Reference): Future[Option[Project]] = database.getProject(ref)

  def getVersions(
      ref: Project.Reference,
      binaryVersions: Seq[BinaryVersion],
      artifactNames: Seq[Artifact.Name],
      stableOnly: Boolean
  ): Future[Seq[Version]] =
    for (artifacts <- getArtifactRefs(ref, binaryVersions.toSet, artifactNames.toSet, stableOnly = stableOnly))
      yield artifacts
        .groupBy(_.version)
        .filter {
          case (_, artifacts) =>
            (artifactNames.isEmpty || artifacts.map(_.name).distinct.size == artifactNames.size) &&
            (binaryVersions.isEmpty || artifacts.map(_.binaryVersion).distinct.size == binaryVersions.size)
        }
        .keys
        .toSeq
        .sorted(Ordering[Version].reverse)

  def getLatestProjectVersion(ref: Project.Reference): Future[Seq[Artifact.Reference]] =
    getHeader(ref).flatMap {
      case None         => Future.successful(Seq.empty)
      case Some(header) => getProjectVersion(ref, header.latestVersion)
    }

  def getProjectVersion(ref: Project.Reference, version: Version): Future[Seq[Artifact.Reference]] =
    database.getProjectArtifactRefs(ref, version)

  def getArtifactRefs(
      ref: Project.Reference,
      binaryVersion: Option[BinaryVersion],
      artifactName: Option[Artifact.Name],
      stableOnly: Boolean
  ): Future[Seq[Artifact.Reference]] = getArtifactRefs(ref, binaryVersion.toSet, artifactName.toSet, stableOnly)

  private def getArtifactRefs(
      ref: Project.Reference,
      binaryVersions: Set[BinaryVersion],
      artifactNames: Set[Artifact.Name],
      stableOnly: Boolean
  ): Future[Seq[Artifact.Reference]] =
    for (artifacts <- database.getProjectArtifactRefs(ref, stableOnly)) yield artifacts.filter { a =>
      (binaryVersions.isEmpty || binaryVersions.contains(a.binaryVersion)) &&
      (artifactNames.isEmpty || artifactNames.contains(a.name))
    }

  def getHeader(ref: Project.Reference): Future[Option[ProjectHeader]] =
    database.getProject(ref).flatMap {
      case None    => Future.successful(None)
      case Some(p) => getHeader(p)
    }

  def getHeader(project: Project): Future[Option[ProjectHeader]] = {
    val ref = project.reference
    for {
      latestArtifacts <- database.getProjectLatestArtifacts(ref)
      versionCount <- database.countVersions(ref)
    } yield ProjectHeader(
      ref,
      latestArtifacts,
      versionCount,
      project.settings.defaultArtifact,
      project.settings.preferStableVersion
    )
  }
}
