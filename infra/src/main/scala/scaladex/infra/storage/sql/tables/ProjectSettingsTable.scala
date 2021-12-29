package scaladex.infra.storage.sql.tables

import doobie.util.Read
import scaladex.core.model.Project
import scaladex.core.model.Project._
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
    "primary_topic",
    "beginner_issues_label"
  )

  val insertOrUpdate: doobie.Update[(Project.Reference, Project.Settings, Project.Settings)] =
    insertOrUpdateRequest(table, referenceFields ++ settingsFields, referenceFields, settingsFields)

  val count: doobie.Query0[Long] =
    selectRequest(table, Seq("COUNT(*)"))

  val settingsRead: Read[Project.Settings] =
    Read[(Organization, Repository, Project.Settings)].map { case (_, _, settings) => settings }
}
