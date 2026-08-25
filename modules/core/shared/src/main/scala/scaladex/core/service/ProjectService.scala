package scaladex.core.service

import scala.concurrent.ExecutionContext

import scaladex.core.model.*
import scaladex.core.model.search.SearchParams
import scaladex.core.util.ScalaExtensions.*

import cats.effect.ContextShift
import cats.effect.IO

class ProjectService(database: WebDatabase, searchEngine: SearchEngine)(using ExecutionContext):
  private given ContextShift[IO] = IO.contextShift(summon[ExecutionContext])

  def getProjects(languages: Seq[Language], platforms: Seq[Platform]): IO[Seq[Project.Reference]] =
    val searchParams = SearchParams(languages = languages, platforms = platforms)
    searchEngine.findRefs(searchParams).toIO

  def getProject(ref: Project.Reference): IO[Option[Project]] = database.getProject(ref)

  def getVersions(
      ref: Project.Reference,
      binaryVersions: Seq[BinaryVersion],
      artifactNames: Seq[Artifact.Name],
      stableOnly: Boolean
  ): IO[Seq[Version]] =
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

  def getLatestProjectVersion(ref: Project.Reference): IO[Seq[Artifact.Reference]] =
    getHeader(ref).flatMap {
      case None => IO.pure(Seq.empty)
      case Some(header) => getProjectVersion(ref, header.latestVersion)
    }

  def getProjectVersion(ref: Project.Reference, version: Version): IO[Seq[Artifact.Reference]] =
    database.getProjectArtifactRefs(ref, version)

  def getArtifactRefs(
      ref: Project.Reference,
      binaryVersion: Option[BinaryVersion],
      artifactName: Option[Artifact.Name],
      stableOnly: Boolean
  ): IO[Seq[Artifact.Reference]] = getArtifactRefs(ref, binaryVersion.toSet, artifactName.toSet, stableOnly)

  private def getArtifactRefs(
      ref: Project.Reference,
      binaryVersions: Set[BinaryVersion],
      artifactNames: Set[Artifact.Name],
      stableOnly: Boolean
  ): IO[Seq[Artifact.Reference]] =
    for artifacts <- database.getProjectArtifactRefs(ref, stableOnly) yield artifacts.filter { a =>
      (binaryVersions.isEmpty || binaryVersions.contains(a.binaryVersion)) &&
      (artifactNames.isEmpty || artifactNames.contains(a.name))
    }

  def getHeader(ref: Project.Reference): IO[Option[ProjectHeader]] =
    database.getProject(ref).flatMap {
      case None => IO.pure(None)
      case Some(p) => getHeader(p)
    }

  def getHeader(project: Project): IO[Option[ProjectHeader]] =
    val ref = project.reference
    for latestArtifacts <- database.getProjectLatestArtifacts(ref)
    yield ProjectHeader(
      ref,
      latestArtifacts,
      project.settings.defaultArtifact,
      project.settings.preferStableVersion
    )
end ProjectService
