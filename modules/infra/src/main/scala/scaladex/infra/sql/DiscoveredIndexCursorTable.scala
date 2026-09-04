package scaladex.infra.sql

import java.time.Instant

import scaladex.core.model.IndexCursor
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*

object DiscoveredIndexCursorTable:
  private[sql] val table = "discovered_index_cursor"

  val select: Query0[IndexCursor] =
    selectRequest(table, Seq("chain_id", "last_incremental"))

  val upsert: Update[(String, Int, Instant)] = Update(
    s"INSERT INTO $table (id, chain_id, last_incremental, updated_at) VALUES (1, ?, ?, ?) " +
      "ON CONFLICT (id) DO UPDATE SET " +
      "chain_id = EXCLUDED.chain_id, last_incremental = EXCLUDED.last_incremental, updated_at = EXCLUDED.updated_at"
  )
end DiscoveredIndexCursorTable
