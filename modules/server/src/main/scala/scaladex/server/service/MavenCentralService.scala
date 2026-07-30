package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.*
import scaladex.core.model.Project
import scaladex.core.service.MavenCentralClient
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*
import scaladex.data.cleanup.NonStandardLib
import scaladex.infra.DataPaths

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after

class MavenCentralService(
    dataPaths: DataPaths,
    database: SchedulerDatabase,
    mavenCentralClient: MavenCentralClient,
    publishProcess: PublishProcess
)(using ExecutionContext, ActorSystem)
    extends LazyLogging:
  private val system = summon[ActorSystem]

  def findNonStandard(): Future[String] =
    val nonStandardLibs = NonStandardLib.load(dataPaths)
    for result <- nonStandardLibs.mapSync { lib =>
        val groupId = Artifact.GroupId(lib.groupId)
        // get should not throw: it is a fixed set of artifactIds
        val artifactId = Artifact.ArtifactId(lib.artifactId)
        for
          knownRefs <- database.getArtifactRefs(groupId)
          inserted <- findAndIndexMissingArtifacts(groupId, artifactId, knownRefs.toSet)
        yield inserted
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
        logger.info(s"${missingVersions.size} artifacts are missing for ${groupId.value}:${artifactId.value}")
      missingPomFiles <- missingVersions.mapSync(ref => mavenCentralClient.getPomFile(ref).map(_.map(ref -> _)))
      publishResult <- missingPomFiles.flatten.mapSync {
        case (mavenRef, (pomFile, creationDate)) =>
          // Add a small delay between publishes to avoid overwhelming the database connection pool
          for
            _ <- delayBetweenPublishes()
            result <- publishProcess.publishPom(mavenRef.toString(), pomFile, creationDate, None)
          yield result
      }
    yield publishResult.count {
      case PublishResult.Success => true
      case _ => false
    }

  private def delayBetweenPublishes(): Future[Unit] =
    // Small delay between publishes to avoid overwhelming the database connection pool
    after(100.millis, system.scheduler)(Future.successful(()))

  def findMissing(): Future[String] =
    for
      // Load group IDs only, then known refs per group — avoid loading the entire artifacts table
      groupIds <- database.getGroupIds().map(_.sorted)
      // we sort just to estimate through the logs the percentage of progress
      result <- groupIds.mapSync(findAndIndexMissingArtifacts(_, None))
    yield s"Inserted ${result.sum} missing poms"

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactNameOpt: Option[Artifact.Name]
  ): Future[Int] =
    for
      knownRefs <- database.getArtifactRefs(groupId).map(_.toSet)
      artifactIds <- mavenCentralClient.getAllArtifactIds(groupId)
      scalaArtifactIds = artifactIds.filter(artifact =>
        artifactNameOpt.forall(_ == artifact.name) && artifact.isScala && artifact.binaryVersion.isValid
      )
      result <- scalaArtifactIds
        .mapSync(id => findAndIndexMissingArtifacts(groupId, id, knownRefs))
    yield result.sum

  def syncOne(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): Future[String] =
    for result <- findAndIndexMissingArtifacts(groupId, artifactNameOpt)
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
