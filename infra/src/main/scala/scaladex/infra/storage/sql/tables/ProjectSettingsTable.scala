package scaladex.infra.storage.sql.tables

import doobie._
import scaladex.core.model.Project
import scaladex.infra.util.DoobieUtils.Mappings._
import scaladex.infra.util.DoobieUtils._

object ProjectSettingsTable {
  val table: String = "project_settings"

  val referenceFields: Seq[String] = Seq("organization", "repository")
  val settingsFields: Seq[String] = Seq(
    "default_stable_version",
    "default_artifact",
    "strict_versions",
    "custom_scaladoc",
    "documentation_links",
    "deprecated",
    "contributors_wanted",
    "artifact_deprecations",
    "cli_artifacts",
    "category",
    "beginner_issues_label"
  )

  val insertOrUpdate: Update[(Project.Reference, Project.Settings, Project.Settings)] =
    insertOrUpdateRequest(table, referenceFields ++ settingsFields, referenceFields, settingsFields)

  val count: Query0[Long] =
    selectRequest(table, Seq("COUNT(*)"))
}
