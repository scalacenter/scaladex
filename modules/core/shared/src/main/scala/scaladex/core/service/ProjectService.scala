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

  /** Direct dependencies of the project at the given version (latest release if not specified), one entry per
    * depended-on project. Returns `None` if the project is unknown.
    */
  def getDependencies(
      ref: Project.Reference,
      version: Option[Version],
      rawPage: PageParams
  ): Future[Option[Page[ProjectDependency]]] =
    val page = PageParams.bounded(rawPage.page, rawPage.size)
    database.getProject(ref).flatMap {
      case None => Future.successful(None)
      case Some(project) =>
        val resolvedVersion = version match
          case some: Some[Version] => Future.successful(some)
          case None => getHeader(project).map(_.map(_.latestVersion))
        resolvedVersion.flatMap {
          case None => Future.successful(Some(Page.empty[ProjectDependency]))
          case Some(v) =>
            for dependencies <- database.getProjectDependencies(ref, v)
            yield Some(paginate(ProjectDependency.collapseByProject(dependencies, _.target), page))
        }
    }
  end getDependencies

  /** Reverse dependencies (dependents) of the project, one entry per dependent project. Returns `None` if the project
    * is unknown.
    */
  def getDependents(ref: Project.Reference, rawPage: PageParams): Future[Option[Page[ProjectDependency]]] =
    val page = PageParams.bounded(rawPage.page, rawPage.size)
    database.getProject(ref).flatMap {
      case None => Future.successful(None)
      case Some(_) =>
        // getProjectReverseDependencies already paginates by distinct source project at the SQL level, so the count of
        // distinct source projects (countProjectDependents) is the right total for this page shape.
        val offset = (page.page - 1) * page.size
        for
          dependents <- database.getProjectReverseDependencies(ref, limit = page.size, offset = offset)
          total <- database.countProjectDependents(ref)
        yield Some(Page(pagination(page, total), ProjectDependency.collapseByProject(dependents, _.source)))
    }
  end getDependents

  private def paginate[A](items: Seq[A], page: PageParams): Page[A] =
    val offset = (page.page - 1) * page.size
    Page(pagination(page, items.size.toLong), items.slice(offset, offset + page.size))

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
