package scaladex.core.model

import java.time.Instant

/** A Maven Central group ID found by the discovery pipeline (see `doc/dev/maven-central-discovery.md`). It is
  * auto-indexed through the normal `syncOne` path; this row only tracks it for the admin review queue.
  */
final case class DiscoveredGroupId(
    groupId: Artifact.GroupId,
    source: DiscoveredGroupId.Source,
    discoveredAt: Instant,
    lastSyncedAt: Option[Instant],
    syncSummary: Option[String],
    projectRefs: Seq[Project.Reference],
    status: DiscoveredGroupId.Status,
    reviewedBy: Option[String],
    reviewedAt: Option[Instant]
)

object DiscoveredGroupId:
  def pending(source: Source, groupId: Artifact.GroupId, now: Instant): DiscoveredGroupId =
    DiscoveredGroupId(groupId, source, now, None, None, Nil, Status.Pending, None, None)

  enum Source:
    case MavenIndex, Manual

  enum Status:
    case Pending, Reviewed, Rejected

  /** Row plus the resolved projects, for rendering the admin panel. */
  final case class View(discovered: DiscoveredGroupId, projects: Seq[Project])
end DiscoveredGroupId

/** Cursor into the Maven Central nexus index chunk chain. */
final case class IndexCursor(chainId: String, lastIncremental: Int)
