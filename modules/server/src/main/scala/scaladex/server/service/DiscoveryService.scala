package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import scaladex.core.model.Artifact
import scaladex.core.model.DiscoveredGroupId
import scaladex.core.model.IndexCursor
import scaladex.core.service.MavenCentralIndexClient
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*

import com.typesafe.scalalogging.LazyLogging

/** Finds Scala group IDs newly published to Maven Central by reading the nexus incremental index, then auto-indexes
  * each one through the existing `MavenCentralService.syncOne`. Every discovered group ID is recorded in
  * `discovered_group_id` for the admin review queue. See `doc/dev/maven-central-discovery.md`.
  */
class DiscoveryService(
    database: SchedulerDatabase,
    indexClient: MavenCentralIndexClient,
    syncGroupId: Artifact.GroupId => Future[String]
)(using ExecutionContext)
    extends LazyLogging:

  // never pull more than this many ~40 MB chunks in one run (a long outage would otherwise be multi-GB)
  private val maxChunksPerRun = 8
  // cap how many freshly-discovered group IDs we sync per run, so a burst can't hammer Maven Central
  private val syncBatchSize = 10

  def discover(): Future[String] = for
    remote <- indexClient.fetchRemoteCursor()
    localOpt <- database.getMavenIndexCursor()
    from = resolveFrom(localOpt, remote)
    result <-
      if from.lastIncremental >= remote.lastIncremental then
        Future.successful(MavenCentralIndexClient.Result(Nil, from.lastIncremental))
      else indexClient.recordsSince(from, remote, maxChunksPerRun)(r => isScalaArtifact(r.artifactId))
    newGroupIds <- selectNewGroupIds(result.records)
    now = Instant.now
    inserted <- database.insertDiscoveredGroupIds(
      newGroupIds.map(g => DiscoveredGroupId.pending(DiscoveredGroupId.Source.MavenIndex, g, now))
    )
    // advance only to the last chunk actually read; a failed chunk keeps its records in scope for the next run
    _ <- database.setMavenIndexCursor(IndexCursor(remote.chainId, result.reachedIncremental))
    synced <- syncPending()
  yield s"Discovered $inserted new group IDs from ${result.records.size} Scala records; synced $synced"

  /** Rewind the cursor so the next `discover()` re-scans the last `chunksBack` chunks (capped at `maxChunksPerRun`).
    * Admin action for backfilling after a deploy or a missed chunk.
    */
  def rewindCursor(chunksBack: Int): Future[String] =
    indexClient
      .fetchRemoteCursor()
      .flatMap: remote =>
        val target = IndexCursor(remote.chainId, math.max(0, remote.lastIncremental - chunksBack))
        database
          .setMavenIndexCursor(target)
          .map: _ =>
            s"Cursor set to chunk ${target.lastIncremental} (remote is ${remote.lastIncremental}); " +
              s"the next discovery run will re-scan up to $maxChunksPerRun chunks"

  /** If the remote chain was rebuilt, or we have no cursor yet, start from "now" (remote.lastIncremental - 1) rather
    * than downloading the 3.2 GB full index.
    */
  private def resolveFrom(localOpt: Option[IndexCursor], remote: IndexCursor): IndexCursor = localOpt match
    case Some(local) if local.chainId == remote.chainId => local
    case Some(local) =>
      logger.warn(s"Maven index chain changed (${local.chainId} -> ${remote.chainId}); skipping the gap")
      IndexCursor(remote.chainId, remote.lastIncremental - 1)
    case None =>
      logger.info("No Maven index cursor yet; starting from the latest chunk")
      IndexCursor(remote.chainId, remote.lastIncremental - 1)

  private def isScalaArtifact(artifactId: String): Boolean =
    val parsed = Artifact.ArtifactId(artifactId)
    parsed.isScala && parsed.binaryVersion.isValid

  private def selectNewGroupIds(records: Seq[MavenCentralIndexClient.Record]): Future[Seq[Artifact.GroupId]] =
    val candidates = records.iterator.filterNot(_.deleted).map(_.groupId).distinct.map(Artifact.GroupId.apply).toSeq
    for
      known <- database.getGroupIds().map(_.toSet)
      recorded <- database.getAllDiscoveredGroupIds().map(_.map(_.groupId).toSet)
    yield candidates.filterNot(known).filterNot(recorded)

  // oldest first, so a backlog drains in publish order instead of starving early entries
  private def syncPending(): Future[Int] = for
    pending <- database.getPendingDiscoveredGroupIdsToSync(syncBatchSize)
    _ <- pending.mapSync(syncAndRecord)
  yield pending.size

  private def syncAndRecord(discovered: DiscoveredGroupId): Future[Unit] =
    val synced = for
      summary <- syncGroupId(discovered.groupId)
      refs <- database.getProjectRefsByGroupId(discovered.groupId)
      _ <- database.updateDiscoveredGroupIdSync(discovered.groupId, Instant.now, summary, refs)
    yield ()
    synced.recoverWith:
      case NonFatal(e) =>
        // record the error but leave last_synced_at unset so the next run retries this group
        logger.warn(s"Failed to sync discovered group ${discovered.groupId.value}, will retry: ${e.getMessage}")
        database.updateDiscoveredGroupIdError(discovered.groupId, s"error: ${e.getMessage}")
  end syncAndRecord
end DiscoveryService
