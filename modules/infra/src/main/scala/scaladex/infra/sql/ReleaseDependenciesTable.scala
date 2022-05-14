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

  val scope = "scope"
  val sourceKeys: Seq[String] = sourcePrimaryKeys :+ "source_release_date"
  val targetKeys: Seq[String] = targetPrimaryKeys :+ "target_release_date"
  private val primaryKeys = sourcePrimaryKeys ++ targetPrimaryKeys :+ scope

  private val fields: Seq[String] = sourceKeys ++ targetKeys :+ scope

  val insertIfNotExists: Update[ReleaseDependency] =
    insertOrUpdateRequest(table, fields, primaryKeys)
}
