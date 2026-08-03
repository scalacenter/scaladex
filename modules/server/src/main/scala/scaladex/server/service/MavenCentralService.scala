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
import scaladex.data.cleanup.NewGroupId
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

  private val groupIdPageSize = 50
  private val artifactRefPageSize = 1000
  private val artifactIdPageSize = 20
  private val pageDelay = 500.millis
  private val publishDelay = 100.millis

  def findNonStandard(): Future[String] =
    val nonStandardLibs = NonStandardLib.load(dataPaths)
    for result <- nonStandardLibs.mapSync { lib =>
        val groupId = Artifact.GroupId(lib.groupId)
        // get should not throw: it is a fixed set of artifactIds
        val artifactId = Artifact.ArtifactId(lib.artifactId)
        for
          knownRefs <- loadKnownRefs(groupId)
          inserted <- findAndIndexMissingArtifacts(groupId, artifactId, knownRefs)
        yield inserted
      }
    yield s"Inserted ${result.sum} missing poms"
  end findNonStandard

  /** Discover and index artifacts from new group IDs submitted by the community.
    *
    * This addresses the gap where artifacts published via Sonatype Central Portal don't trigger the legacy webhook, and
    * the scheduled backfill job only scans group IDs already in the database.
    *
    * Library authors can submit their group IDs to new-groups.json in scaladex-contrib to have their artifacts
    * discovered.
    */
  def findNewGroups(): Future[String] =
    val newGroupIds = NewGroupId.load(dataPaths)
    if newGroupIds.isEmpty then Future.successful("No new group IDs to discover")
    else
      for
        knownRefs <- database.getArtifactRefs()
        knownGroupIds = knownRefs.map(_.groupId).toSet
        // Filter to only process group IDs not already in the database
        newGroups = newGroupIds.filterNot(ng => knownGroupIds.contains(Artifact.GroupId(ng.groupId)))
        _ = if newGroups.nonEmpty then
          logger.info(s"Discovering ${newGroups.size} new group IDs: ${newGroups.map(_.groupId).mkString(", ")}")
        result <- newGroups.mapSync { ng =>
          val groupId = Artifact.GroupId(ng.groupId)
          findAndIndexMissingArtifacts(groupId, None, knownRefs.toSet)
        }
      yield
        val totalInserted = result.sum
        if totalInserted > 0 then s"Discovered ${newGroups.size} new group IDs, inserted $totalInserted poms"
        else if newGroups.isEmpty then s"All ${newGroupIds.size} group IDs from new-groups.json are already known"
        else s"Checked ${newGroups.size} new group IDs, no new artifacts found"
    end if
  end findNewGroups

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
          for
            _ <- delay(publishDelay)
            result <- publishProcess.publishPom(mavenRef.toString(), pomFile, creationDate, None)
          yield result
      }
    yield publishResult.count {
      case PublishResult.Success => true
      case _ => false
    }

  def findMissing(): Future[String] =
    def loop(page: Int, totalInserted: Int): Future[Int] =
      for
        batch <- database.getGroupIds(limit = groupIdPageSize, offset = page * groupIdPageSize)
        _ = logger.info(s"Processing group ID page $page (${batch.size} groups)")
        inserted <- batch.mapSync(g => findAndIndexMissingArtifacts(g, None)).map(_.sum)
        total = totalInserted + inserted
        result <-
          if batch.size == groupIdPageSize then delay(pageDelay).flatMap(_ => loop(page + 1, total))
          else Future.successful(total)
      yield result

    loop(0, 0).map(n => s"Inserted $n missing poms")
  end findMissing

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactNameOpt: Option[Artifact.Name]
  ): Future[Int] =
    for
      knownRefs <- loadKnownRefs(groupId)
      artifactIds <- mavenCentralClient.getAllArtifactIds(groupId)
      scalaArtifactIds = artifactIds.filter(artifact =>
        artifactNameOpt.forall(_ == artifact.name) && artifact.isScala && artifact.binaryVersion.isValid
      )
      result <- processPages(scalaArtifactIds, artifactIdPageSize) { batch =>
        batch.mapSync(id => findAndIndexMissingArtifacts(groupId, id, knownRefs)).map(_.sum)
      }
    yield result

  def syncOne(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): Future[String] =
    for result <- findAndIndexMissingArtifacts(groupId, artifactNameOpt)
    yield s"Inserted $result poms"

  /** Load known refs for a group in pages to keep each DB query small. */
  private def loadKnownRefs(groupId: GroupId): Future[Set[Artifact.Reference]] =
    def loop(page: Int, acc: Set[Artifact.Reference]): Future[Set[Artifact.Reference]] =
      for
        batch <- database.getArtifactRefs(groupId, limit = artifactRefPageSize, offset = page * artifactRefPageSize)
        next = acc ++ batch
        result <-
          if batch.size == artifactRefPageSize then loop(page + 1, next)
          else Future.successful(next)
      yield result
    loop(0, Set.empty)
  end loadKnownRefs

  /** Process items in pages, with a short delay between full pages. */
  private def processPages[A](items: Seq[A], pageSize: Int)(process: Seq[A] => Future[Int]): Future[Int] =
    def loop(page: Int, total: Int): Future[Int] =
      val batch = items.slice(page * pageSize, (page + 1) * pageSize)
      if batch.isEmpty then Future.successful(total)
      else
        for
          inserted <- process(batch)
          next = total + inserted
          result <-
            if batch.size == pageSize then delay(pageDelay).flatMap(_ => loop(page + 1, next))
            else Future.successful(next)
        yield result
    end loop
    loop(0, 0)
  end processPages

  private def delay(duration: FiniteDuration): Future[Unit] =
    after(duration, system.scheduler)(Future.successful(()))

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
