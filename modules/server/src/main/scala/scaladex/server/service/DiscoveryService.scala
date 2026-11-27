package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import scaladex.core.model.Artifact
import scaladex.core.model.DiscoveredGroupId
import scaladex.core.model.IndexCursor
import scaladex.core.model.Version
import scaladex.core.service.MavenCentralIndexClient
import scaladex.core.service.MavenCentralIndexClient.Record
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*

import com.typesafe.scalalogging.LazyLogging

/** Finds Scala artifacts newly published to Maven Central by reading the nexus incremental index, and indexes them.
  * This is the primary way new releases reach Scaladex: each index record names an exact `groupId:artifactId:version`,
  * so only the POMs of versions we do not have yet are downloaded (no directory listings or `maven-metadata.xml`).
  * Group IDs seen for the first time are also recorded in `discovered_group_id` for the admin review queue. See
  * `doc/dev/maven-central-discovery.md`.
  *
  * Chunks are processed one at a time and the cursor is saved after each, so a large backlog (a fresh deploy
  * backfilling from chunk 0, or a long outage) never holds more than one chunk's records in memory and a failure only
  * loses the chunk in progress. Request pacing comes from the shared `CommonAkkaHttpClient` throttle.
  */
class DiscoveryService(
    database: SchedulerDatabase,
    indexClient: MavenCentralIndexClient,
    indexRefs: Seq[Artifact.Reference] => Future[Int],
    syncGroupId: Artifact.GroupId => Future[String],
    nonStandardArtifacts: () => Set[(String, String)] = () => Set.empty
)(using ExecutionContext)
    extends LazyLogging:
  import DiscoveryService.Stats

  // defensive cap on one run's DB query / duration, not a throttle (the shared CommonAkkaHttpClient paces the
  // actual requests) -- comfortably above any realistic backlog
  private val syncBatchSize = 1000

  def discover(): Future[String] = for
    remote <- indexClient.fetchRemoteCursor()
    localOpt <- database.getMavenIndexCursor()
    from = resolveFrom(localOpt, remote)
    nonStandard = nonStandardArtifacts()
    stats <- scanChunks(from, remote, r => isScalaArtifact(r.artifactId) || nonStandard((r.groupId, r.artifactId)))
    retried <- syncPending()
  yield s"Scanned ${stats.chunks} chunks (${stats.records} Scala records): indexed ${stats.inserted} new POMs, " +
    s"discovered ${stats.newGroupIds} new group IDs; retried $retried pending group IDs"

  private def scanChunks(from: IndexCursor, remote: IndexCursor, keep: Record => Boolean): Future[Stats] =
    def loop(cursor: IndexCursor, acc: Stats): Future[Stats] =
      if cursor.lastIncremental >= remote.lastIncremental then Future.successful(acc)
      else
        indexClient
          .recordsSince(cursor, remote, maxChunks = 1)(keep)
          .flatMap: result =>
            // the client already logged why; the chunk stays in scope for the next run
            if result.reachedIncremental <= cursor.lastIncremental then Future.successful(acc)
            else
              val next = IndexCursor(remote.chainId, result.reachedIncremental)
              for
                chunkStats <- processRecords(result.records)
                // advance only once the chunk's artifacts are indexed, so a failure re-reads it next run
                _ <- database.setMavenIndexCursor(next)
                _ = logger.info(s"Processed Maven index chunk ${next.lastIncremental}: $chunkStats")
                total <- loop(next, acc + chunkStats)
              yield total
              end for
    loop(from, Stats.empty)
  end scanChunks

  private def processRecords(records: Seq[Record]): Future[Stats] =
    // one artifact appears once per file (jar, sources, javadoc...), hence the distinct
    val refsByGroup = records.iterator
      .filterNot(_.deleted)
      .map(r => Artifact.Reference(Artifact.GroupId(r.groupId), Artifact.ArtifactId(r.artifactId), Version(r.version)))
      .distinct
      .toSeq
      .groupBy(_.groupId)
    for
      recorded <- database.getAllDiscoveredGroupIds().map(_.map(_.groupId).toSet)
      perGroup <- refsByGroup.toSeq.sortBy(_._1.value).mapSync((groupId, refs) => processGroup(groupId, refs, recorded))
    yield perGroup.foldLeft(Stats(chunks = 1, records = records.size))(_ + _)
  end processRecords

  private def processGroup(
      groupId: Artifact.GroupId,
      refs: Seq[Artifact.Reference],
      recorded: Set[Artifact.GroupId]
  ): Future[Stats] =
    database
      .getArtifactRefs(groupId)
      .flatMap: knownSeq =>
        val known = knownSeq.toSet
        val missing = refs.filterNot(known)
        val isNewGroup = known.isEmpty && !recorded.contains(groupId)
        if isNewGroup then indexNewGroup(groupId, missing)
        else if missing.isEmpty then Future.successful(Stats.empty)
        else indexRefs(missing).map(n => Stats(inserted = n))

  private def indexNewGroup(groupId: Artifact.GroupId, refs: Seq[Artifact.Reference]): Future[Stats] =
    val pending = DiscoveredGroupId.pending(DiscoveredGroupId.Source.MavenIndex, groupId, Instant.now)
    database
      .insertDiscoveredGroupIds(Seq(pending))
      .flatMap: _ =>
        val indexed = for
          inserted <- indexRefs(refs)
          projectRefs <- database.getProjectRefsByGroupId(groupId)
          _ <- database.updateDiscoveredGroupIdSync(groupId, Instant.now, s"Inserted $inserted poms", projectRefs)
        yield Stats(inserted = inserted, newGroupIds = 1)
        indexed.recoverWith:
          case NonFatal(e) =>
            // last_synced_at stays unset, so syncPending retries the whole group with a full sync
            logger.warn(s"Failed to index discovered group ${groupId.value}, will retry: ${e.getMessage}")
            database
              .updateDiscoveredGroupIdError(groupId, s"error: ${e.getMessage}")
              .map(_ => Stats(newGroupIds = 1))
  end indexNewGroup

  /** Rewind the cursor so the next `discover()` re-scans the last `chunksBack` chunks. Admin action for backfilling
    * after a deploy or a missed chunk.
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
              s"the next discovery run will re-scan the missed chunks"

  /** If the remote chain was rebuilt, start from "now" (remote.lastIncremental - 1) rather than downloading the 3.2 GB
    * full index. If we have no cursor yet, start from chunk 0 instead: the chain goes back to 2012-06-15, well before
    * the OSSRH-to-Central-Portal migration that actually caused the discovery gap (new namespace registration moved to
    * the Portal from 2024-02-01), but scanning the full chain is cheap enough (paced by the shared
    * `CommonAkkaHttpClient`, not by a per-run chunk cap) that there is no need to rely on that date estimate.
    */
  private def resolveFrom(localOpt: Option[IndexCursor], remote: IndexCursor): IndexCursor = localOpt match
    case Some(local) if local.chainId == remote.chainId => local
    case Some(local) =>
      logger.warn(s"Maven index chain changed (${local.chainId} -> ${remote.chainId}); skipping the gap")
      IndexCursor(remote.chainId, remote.lastIncremental - 1)
    case None =>
      logger.info("No Maven index cursor yet; starting from the beginning of the chain to backfill history")
      IndexCursor(remote.chainId, 0)

  private def isScalaArtifact(artifactId: String): Boolean =
    val parsed = Artifact.ArtifactId(artifactId)
    parsed.isScala && parsed.binaryVersion.isValid

  /** Retry discovered group IDs that failed to index, with a full listing-based sync. Oldest first, so a backlog drains
    * in publish order instead of starving early entries.
    */
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

object DiscoveryService:
  final case class Stats(chunks: Int = 0, records: Int = 0, inserted: Int = 0, newGroupIds: Int = 0):
    def +(other: Stats): Stats =
      Stats(chunks + other.chunks, records + other.records, inserted + other.inserted, newGroupIds + other.newGroupIds)
    override def toString: String = s"$records Scala records, $inserted new POMs, $newGroupIds new group IDs"
  object Stats:
    val empty: Stats = Stats()
end DiscoveryService
