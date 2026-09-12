package scaladex.infra.sql

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.DiscoveredGroupId
import scaladex.core.model.Project
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*

object DiscoveredGroupIdTable:
  private[sql] val table = "discovered_group_id"
  private val fields = Seq(
    "group_id",
    "source",
    "discovered_at",
    "last_synced_at",
    "sync_summary",
    "project_refs",
    "status",
    "reviewed_by",
    "reviewed_at"
  )

  // insert new rows only: an already-known group ID keeps its current status (incl. 'rejected')
  val insertIfNotExists: Update[DiscoveredGroupId] =
    insertOrUpdateRequest(table, fields, Seq("group_id"))

  val selectAll: Query0[DiscoveredGroupId] =
    selectRequest(table, fields, orderBy = Some("discovered_at DESC"))

  val selectByStatus: Query[DiscoveredGroupId.Status, DiscoveredGroupId] =
    selectRequest1(table, fields, keys = Seq("status"), orderBy = Some("discovered_at DESC"))

  private val fieldsStr = fields.mkString(", ")

  // sync queue: pending + never synced, oldest first, bounded (param = limit)
  val selectPendingToSync: Query[Long, DiscoveredGroupId] = Query(
    s"SELECT $fieldsStr FROM $table WHERE status = 'Pending' AND last_synced_at IS NULL " +
      "ORDER BY discovered_at ASC LIMIT ?"
  )

  // review queue: all pending, newest first, bounded (param = limit)
  val selectPendingToReview: Query[Long, DiscoveredGroupId] =
    Query(s"SELECT $fieldsStr FROM $table WHERE status = 'Pending' ORDER BY discovered_at DESC LIMIT ?")

  val updateSync: Update[(Instant, String, Seq[Project.Reference], Artifact.GroupId)] =
    updateRequest(table, Seq("last_synced_at", "sync_summary", "project_refs"), Seq("group_id"))

  val updateError: Update[(String, Artifact.GroupId)] =
    updateRequest(table, Seq("sync_summary"), Seq("group_id"))

  val updateStatus: Update[(DiscoveredGroupId.Status, String, Instant, Artifact.GroupId)] =
    updateRequest(table, Seq("status", "reviewed_by", "reviewed_at"), Seq("group_id"))
end DiscoveredGroupIdTable
