package scaladex.infra.storage.sql.tables

import java.time.Instant

import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.infra.util.DoobieUtils.Fragments._
import scaladex.infra.util.DoobieUtils.Mappings._
import scaladex.infra.util.DoobieUtils._

object ProjectTable {
  private val _ = documentationLinksMeta

  private val table: String = "projects"
  private val tableFr: Fragment = Fragment.const0(table)
  private val githubStatusFields =
    Seq("github_status", "github_update_date", "new_organization", "new_repository", "error_code", "error_message")
  private val fields: Seq[String] = Seq("organization", "repository", "creation_date") ++ githubStatusFields
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(ref: Project.Reference): Fragment =
    fr0"${ref.organization}, ${ref.repository}"

  private val allFields: Seq[String] = fields.map("p." + _) ++
    GithubInfoTable.fields.map("g." + _) ++
    ProjectSettingsTable.fields.drop(2).map("f." + _)
  private val allFieldsFr: Fragment = Fragment.const0(allFields.mkString(", "))

  private val fullTable: String =
    s"$table p " +
      s"LEFT JOIN ${GithubInfoTable.table} g ON p.organization = g.organization AND p.repository = g.repository " +
      s"LEFT JOIN ${ProjectSettingsTable.table} f ON p.organization = f.organization AND p.repository = f.repository"

  private val fullTableFr: Fragment = Fragment.const(fullTable)

  val insertIfNotExists: Update[(Project.Reference, GithubStatus)] =
    insertOrUpdateRequest(
      table,
      Seq("organization", "repository") ++ githubStatusFields,
      Seq("organization", "repository")
    )

  val updateCreated: Update[(Instant, Project.Reference)] =
    updateRequest(table, Seq("creation_date"), Seq("organization", "repository"))

  val updateGithubStatus: Update[(GithubStatus, Project.Reference)] =
    updateRequest(table, githubStatusFields, Seq("organization", "repository"))

  val countProjects: Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectOne(ref: Project.Reference): Query0[Project] =
    buildSelect(
      fullTableFr,
      allFieldsFr,
      fr0"WHERE p.organization = ${ref.organization} AND p.repository = ${ref.repository}"
    ).query[Project]

  def selectLatestProjects(limit: Int): Query0[Project] =
    buildSelect(
      fullTableFr,
      allFieldsFr,
      fr0"WHERE creation_date IS NOT NULL ORDER BY creation_date DESC LIMIT $limit"
    ).query[Project]

  val selectAllProjectRef: Query0[Project.Reference] =
    selectRequest(table, Seq("organization", "repository"))

  def selectAllProjects: Query0[Project] =
    selectRequest(fullTable.toString, allFields)
}
