package scaladex.infra.sql

import doobie._
import scaladex.core.model.Project
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

object ProjectSettingsTable {
  val table: String = "project_settings"

  val referenceFields: Seq[String] = Seq("organization", "repository")
  val settingsFields: Seq[String] = Seq(
    "prefer_stable_version",
    "default_artifact",
    "custom_scaladoc",
    "documentation_links",
    "contributors_wanted",
    "deprecated_artifacts",
    "cli_artifacts",
    "category",
    "chatroom"
  )

  val insertOrUpdate: Update[(Project.Reference, Project.Settings, Project.Settings)] =
    insertOrUpdateRequest(table, referenceFields ++ settingsFields, referenceFields, settingsFields)

  val count: Query0[Long] =
    selectRequest(table, Seq("COUNT(*)"))
}
