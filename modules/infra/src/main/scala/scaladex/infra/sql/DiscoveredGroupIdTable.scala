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
    "project_refs"
  )

  // insert new rows only: an already-known group ID is never re-discovered
  val insertIfNotExists: Update[DiscoveredGroupId] =
    insertOrUpdateRequest(table, fields, Seq("group_id"))

  val selectAll: Query0[DiscoveredGroupId] =
    selectRequest(table, fields, orderBy = Some("discovered_at DESC"))

  private val fieldsStr = fields.mkString(", ")

  // sync queue: never synced, oldest first, bounded (param = limit)
  val selectPendingToSync: Query[Long, DiscoveredGroupId] = Query(
    s"SELECT $fieldsStr FROM $table WHERE last_synced_at IS NULL ORDER BY discovered_at ASC LIMIT ?"
  )

  val updateSync: Update[(Instant, String, Seq[Project.Reference], Artifact.GroupId)] =
    updateRequest(table, Seq("last_synced_at", "sync_summary", "project_refs"), Seq("group_id"))

  val updateError: Update[(String, Artifact.GroupId)] =
    updateRequest(table, Seq("sync_summary"), Seq("group_id"))
end DiscoveredGroupIdTable
