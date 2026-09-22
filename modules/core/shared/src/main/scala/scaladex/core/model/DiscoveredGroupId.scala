package scaladex.core.model

import java.time.Instant

/** A Maven Central group ID found by the discovery pipeline (see `doc/dev/maven-central-discovery.md`). It is
  * auto-indexed through the normal `syncOne` path; this row tracks discovery/sync bookkeeping so a group is never
  * re-discovered once seen, and so a failed sync is retried.
  */
final case class DiscoveredGroupId(
    groupId: Artifact.GroupId,
    source: DiscoveredGroupId.Source,
    discoveredAt: Instant,
    lastSyncedAt: Option[Instant],
    syncSummary: Option[String],
    projectRefs: Seq[Project.Reference]
)

object DiscoveredGroupId:
  def pending(source: Source, groupId: Artifact.GroupId, now: Instant): DiscoveredGroupId =
    DiscoveredGroupId(groupId, source, now, None, None, Nil)

  enum Source:
    case MavenIndex, Manual
end DiscoveredGroupId

/** Cursor into the Maven Central nexus index chunk chain. */
final case class IndexCursor(chainId: String, lastIncremental: Int)
