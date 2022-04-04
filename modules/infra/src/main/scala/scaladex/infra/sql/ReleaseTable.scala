package scaladex.infra.sql

import doobie.util.update.Update
import scaladex.core.model.Release
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.insertOrUpdateRequest

object ReleaseTable {
  private val table: String = "releases"
  private val primaryKeys: Seq[String] =
    Seq(
      "organization",
      "repository",
      "platform",
      "language_version",
      "version"
    )
  val fields: Seq[String] =
    primaryKeys ++ Seq(
      "release_date"
    )
  val insertIfNotExists: Update[Release] =
    insertOrUpdateRequest(table, fields, primaryKeys)
}
