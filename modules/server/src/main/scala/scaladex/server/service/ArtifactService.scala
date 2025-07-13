package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.*
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*

import com.typesafe.scalalogging.LazyLogging

class ArtifactService(database: SchedulerDatabase)(using ExecutionContext) extends LazyLogging:
  def getVersions(
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      stableOnly: Boolean
  ): Future[Seq[Version]] =
    database.getArtifactVersions(groupId, artifactId, stableOnly)

  def getLatestArtifact(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId
  ): Future[Option[Artifact]] =
    database.getLatestArtifact(ref, groupId, artifactId)

  def getLatestArtifact(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Option[Artifact]] =
    database
      .getLatestArtifacts(groupId, artifactId)
      .map(artifacts => artifacts.maxOption(using Ordering.by(_.releaseDate)))

  def getArtifact(ref: Artifact.Reference): Future[Option[Artifact]] =
    database.getArtifact(ref)

  def insertArtifact(artifact: Artifact, dependencies: Seq[ArtifactDependency]): Future[Boolean] =
    val unknownStatus = GithubStatus.Unknown(Instant.now)
    for
      isNewProject <- database.insertProjectRef(artifact.projectRef, unknownStatus)
      project <- database.getProject(artifact.projectRef).map(_.get)
      _ <- database.insertArtifact(artifact)
      _ <- database.insertDependencies(dependencies)
      _ <- updateLatestVersion(
        project.reference,
        artifact.groupId,
        artifact.artifactId,
        project.settings.preferStableVersion
      )
    yield isNewProject
    end for
  end insertArtifact

  def moveAll(): Future[String] =
    for
      projectStatuses <- database.getAllProjectsStatuses()
      moved = projectStatuses.collect { case (ref, GithubStatus.Moved(_, dest)) => ref -> dest }
      total <- moved
        .map {
          case (source, dest) =>
            database
              .getProjectArtifactRefs(source, stableOnly = false)
              .flatMap(artifacts => database.updateArtifacts(artifacts, dest))
        }
        .sequence
        .map(_.sum)
    yield s"Moved $total artifacts"

  def updateAllLatestVersions(): Future[String] =
    for
      projectStatuses <- database.getAllProjectsStatuses()
      refs = projectStatuses.collect { case (ref, status) if status.isOk || status.isUnknown || status.isFailed => ref }
      _ = logger.info(s"Updating latest versions of ${refs.size} projects")
      total <- refs.mapSync(updateLatestVersions).map(_.sum)
    yield s"Updated $total artifacts in ${refs.size} projects"

  def updateLatestVersions(ref: Project.Reference): Future[Int] =
    for
      project <- database.getProject(ref).map(_.get)
      total <- updateLatestVersions(ref, project.settings.preferStableVersion)
    yield total

  def updateLatestVersions(ref: Project.Reference, preferStableVersion: Boolean): Future[Int] =
    for
      artifactIds <- database.getArtifactIds(ref)
      _ <- artifactIds.mapSync {
        case (groupId, artifactId) => updateLatestVersion(ref, groupId, artifactId, preferStableVersion)
      }
    yield artifactIds.size

  def updateLatestVersion(
      ref: Project.Reference,
      groupId: Artifact.GroupId,
      artifactId: Artifact.ArtifactId,
      preferStableVersion: Boolean
  ): Future[Unit] =
    for
      artifacts <- database.getArtifacts(ref, groupId, artifactId)
      latestVersion = computeLatestVersion(artifacts.map(_.version), preferStableVersion)
      _ <- database.updateLatestVersion(ref, Artifact.Reference(groupId, artifactId, latestVersion))
    yield ()

  def computeLatestVersion(versions: Seq[Version], preferStableVersion: Boolean): Version =
    def maxStable = versions.filter(_.isStable).maxOption
    def max = versions.max
    if preferStableVersion then maxStable.getOrElse(max) else max
end ArtifactService
