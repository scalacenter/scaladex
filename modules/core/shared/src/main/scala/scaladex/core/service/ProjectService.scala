package scaladex.core.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.*
import scaladex.core.model.search.Page
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.Pagination
import scaladex.core.model.search.SearchParams

class ProjectService(database: WebDatabase, searchEngine: SearchEngine)(using ExecutionContext):
  def getProjects(languages: Seq[Language], platforms: Seq[Platform]): Future[Seq[Project.Reference]] =
    val searchParams = SearchParams(languages = languages, platforms = platforms)
    searchEngine.findRefs(searchParams)

  def getProject(ref: Project.Reference): Future[Option[Project]] = database.getProject(ref)

  def getVersions(
      ref: Project.Reference,
      binaryVersions: Seq[BinaryVersion],
      artifactNames: Seq[Artifact.Name],
      stableOnly: Boolean
  ): Future[Seq[Version]] =
    for artifacts <- getArtifactRefs(ref, binaryVersions.toSet, artifactNames.toSet, stableOnly = stableOnly)
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
      case None => Future.successful(Seq.empty)
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
    for artifacts <- database.getProjectArtifactRefs(ref, stableOnly) yield artifacts.filter { a =>
      (binaryVersions.isEmpty || binaryVersions.contains(a.binaryVersion)) &&
      (artifactNames.isEmpty || artifactNames.contains(a.name))
    }

  def getSettings(ref: Project.Reference): Future[Option[Project.Settings]] =
    database.getProject(ref).map(_.map(_.settings))

  /** Direct dependencies at the given version (latest release if not specified), one entry per depended-on project. */
  def getDependencies(
      ref: Project.Reference,
      version: Option[Version],
      page: PageParams
  ): Future[Option[Page[ProjectDependency]]] =
    database.getProject(ref).flatMap {
      case None => Future.successful(None)
      case Some(project) =>
        val resolvedVersion = version match
          case some @ Some(_) => Future.successful(some)
          case None => getHeader(project).map(_.map(_.latestVersion))
        resolvedVersion.flatMap {
          case None => Future.successful(Some(Page.empty[ProjectDependency]))
          case Some(v) =>
            val offset = (page.page - 1) * page.size
            for
              dependencies <- database.getProjectDependencies(ref, v, page.size, offset)
              total <- database.countProjectDependencies(ref, v)
            yield Some(Page(pagination(page, total), ProjectDependency.collapseByProject(dependencies, _.target)))
        }
    }
  end getDependencies

  /** Reverse dependencies (dependents), one entry per dependent project. */
  def getDependents(ref: Project.Reference, page: PageParams): Future[Option[Page[ProjectDependency]]] =
    database.getProject(ref).flatMap {
      case None => Future.successful(None)
      case Some(_) =>
        val offset = (page.page - 1) * page.size
        for
          dependents <- database.getProjectReverseDependencies(ref, limit = page.size, offset = offset)
          total <- database.countProjectDependents(ref)
        yield Some(Page(pagination(page, total), ProjectDependency.collapseByProject(dependents, _.source)))
    }
  end getDependents

  private def pagination(page: PageParams, total: Long): Pagination =
    val pageCount = math.ceil(total.toDouble / page.size).toInt.max(1)
    Pagination(current = page.page, pageCount = pageCount, totalSize = total)

  def getHeader(ref: Project.Reference): Future[Option[ProjectHeader]] =
    database.getProject(ref).flatMap {
      case None => Future.successful(None)
      case Some(p) => getHeader(p)
    }

  def getHeader(project: Project): Future[Option[ProjectHeader]] =
    val ref = project.reference
    for latestArtifacts <- database.getProjectLatestArtifacts(ref)
    yield ProjectHeader(
      ref,
      latestArtifacts,
      project.settings.defaultArtifact,
      project.settings.preferStableVersion
    )
end ProjectService
