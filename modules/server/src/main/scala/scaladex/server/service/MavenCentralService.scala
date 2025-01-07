package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.*
import scaladex.core.model.Project
import scaladex.core.service.MavenCentralClient
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*
import scaladex.data.cleanup.NonStandardLib
import scaladex.infra.DataPaths

class MavenCentralService(
    dataPaths: DataPaths,
    database: SchedulerDatabase,
    mavenCentralClient: MavenCentralClient,
    publishProcess: PublishProcess
)(implicit ec: ExecutionContext)
    extends LazyLogging:

  def findNonStandard(): Future[String] =
    val nonStandardLibs = NonStandardLib.load(dataPaths)
    for
      knownRefs <- database.getArtifactRefs()
      result <- nonStandardLibs.mapSync { lib =>
        val groupId = Artifact.GroupId(lib.groupId)
        // get should not throw: it is a fixed set of artifactIds
        val artifactId = Artifact.ArtifactId(lib.artifactId)
        findAndIndexMissingArtifacts(groupId, artifactId, knownRefs.toSet)
      }
    yield s"Inserted ${result.sum} missing poms"
  end findNonStandard

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactId: ArtifactId,
      knownRefs: Set[Artifact.Reference]
  ): Future[Int] =
    for
      versions <- mavenCentralClient.getAllVersions(groupId, artifactId)
      missingVersions = versions.map(Artifact.Reference(groupId, artifactId, _)).filterNot(knownRefs)
      _ = if missingVersions.nonEmpty then
        logger.warn(s"${missingVersions.size} artifacts are missing for ${groupId.value}:${artifactId.value}")
      missingPomFiles <- missingVersions.mapSync(ref => mavenCentralClient.getPomFile(ref).map(_.map(ref -> _)))
      publishResult <- missingPomFiles.flatten.mapSync {
        case (mavenRef, (pomFile, creationDate)) =>
          publishProcess.publishPom(mavenRef.toString(), pomFile, creationDate, None)
      }
    yield publishResult.count {
      case PublishResult.Success => true
      case _ => false
    }

  def findMissing(): Future[String] =
    for
      knownRefs <- database.getArtifactRefs().map(_.toSet)
      groupIds = knownRefs.map(_.groupId).toSeq.sorted
      // we sort just to estimate through the logs the percentage of progress
      result <- groupIds.mapSync(g => findAndIndexMissingArtifacts(g, None, knownRefs))
    yield s"Inserted ${result.sum} missing poms"

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactNameOpt: Option[Artifact.Name],
      knownRefs: Set[Artifact.Reference]
  ): Future[Int] =
    for
      artifactIds <- mavenCentralClient.getAllArtifactIds(groupId)
      scalaArtifactIds = artifactIds.filter(artifact =>
        artifactNameOpt.forall(_ == artifact.name) && artifact.isScala && artifact.binaryVersion.isValid
      )
      result <- scalaArtifactIds
        .mapSync(id => findAndIndexMissingArtifacts(groupId, id, knownRefs))
    yield result.sum

  def syncOne(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): Future[String] =
    for
      knownRefs <- database.getArtifactRefs()
      result <- findAndIndexMissingArtifacts(groupId, artifactNameOpt, knownRefs.toSet)
    yield s"Inserted $result poms"

  def republishArtifacts(): Future[String] =
    for
      projectStatuses <- database.getAllProjectsStatuses()
      refs = projectStatuses.collect { case (ref, status) if status.isOk || status.isUnknown || status.isFailed => ref }
      counts <- refs.mapSync(republishArtifacts)
    yield
      val successes = counts.map(_._1).sum
      val failures = counts.map(_._2).sum
      s"Re-published $successes artifacts ($failures failures)."

  private def republishArtifacts(projectRef: Project.Reference): Future[(Int, Int)] =
    for
      refs <- database.getProjectArtifactRefs(projectRef, stableOnly = false)
      publishResult <- refs.mapSync(republishArtifact(projectRef, _))
    yield
      val successes = publishResult.count(_ == PublishResult.Success)
      val failures = publishResult.size - successes
      logger.info(s"Re-published $successes artifacts of $projectRef ($failures failures)")
      (successes, failures)

  private def republishArtifact(projectRef: Project.Reference, ref: Artifact.Reference): Future[PublishResult] =
    mavenCentralClient.getPomFile(ref).flatMap {
      case Some((pomFile, creationDate)) => publishProcess.republishPom(projectRef, ref, pomFile, creationDate)
      case _ => Future.successful(PublishResult.InvalidPom)
    }
end MavenCentralService
