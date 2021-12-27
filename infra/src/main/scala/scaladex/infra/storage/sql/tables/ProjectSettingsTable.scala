package scaladex.infra.storage.sql.tables

import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment
import scaladex.core.model.Project
import scaladex.core.model.Project._
import scaladex.infra.util.DoobieUtils.Fragments._
import scaladex.infra.util.DoobieUtils.Mappings._

object ProjectSettingsTable {
  locally { val _ = contributorMeta }
  val fields: Seq[String] = Seq(
    "organization",
    "repository",
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

  val table: String = "project_settings"
  val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: Project.Reference, userData: Project.Settings): Fragment =
    fr0"${p.organization}, ${p.repository}, ${userData.defaultStableVersion}, ${userData.defaultArtifact}," ++
      fr0" ${userData.strictVersions}, ${userData.customScalaDoc}, ${userData.documentationLinks}, ${userData.deprecated}, ${userData.contributorsWanted}," ++
      fr0" ${userData.artifactDeprecations}, ${userData.cliArtifacts}, ${userData.primaryTopic}, ${userData.beginnerIssuesLabel}"

  def insertOrUpdate(ref: Project.Reference)(settings: Project.Settings): doobie.Update0 = {
    val onConflict = fr0"organization, repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      tableFr,
      fieldsFr,
      values(ref, settings),
      onConflict,
      doAction
    ).update
  }

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def count(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  val settingsRead: Read[Project.Settings] =
    Read[(Organization, Repository, Project.Settings)].map { case (_, _, settings) => settings }
}
