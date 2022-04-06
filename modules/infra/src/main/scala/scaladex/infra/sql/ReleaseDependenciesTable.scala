package scaladex.infra.sql

import doobie.util.update.Update
import scaladex.core.model.ReleaseDependency
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.insertOrUpdateRequest

object ReleaseDependenciesTable {
  private val table: String = "release_dependencies"
  private val sourcePrimaryKeys: Seq[String] =
    Seq(
      "source_organization",
      "source_repository",
      "source_platform",
      "source_language_version",
      "source_version"
    )

  private val targetPrimaryKeys: Seq[String] =
    Seq(
      "target_organization",
      "target_repository",
      "target_platform",
      "target_language_version",
      "target_version"
    )
  private val fields: Seq[String] =
    sourcePrimaryKeys ++ Seq("source_release_date") ++ targetPrimaryKeys ++ Seq("target_release_date", "scope")

  private val primaryKeys = (sourcePrimaryKeys ++ targetPrimaryKeys) :+ "scope"

  val insertIfNotExists: Update[ReleaseDependency] =
    insertOrUpdateRequest(table, fields, primaryKeys)
}
