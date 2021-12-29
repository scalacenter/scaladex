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
  locally { val _ = contributorMeta }

  private val table: String = "projects"
  private val tableFr: Fragment = Fragment.const0(table)

  private val referenceFields = Seq("organization", "repository")
  private val githubStatusFields =
    Seq("github_status", "github_update_date", "new_organization", "new_repository", "error_code", "error_message")

  private val fields: Seq[String] = referenceFields ++ Seq("creation_date") ++ githubStatusFields

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
      referenceFields ++ githubStatusFields,
      referenceFields
    )

  val updateCreated: Update[(Instant, Project.Reference)] =
    updateRequest(table, Seq("creation_date"), referenceFields)

  val updateGithubStatus: Update[(GithubStatus, Project.Reference)] =
    updateRequest(table, githubStatusFields, referenceFields)

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
      fr0"WHERE p.creation_date IS NOT NULL ORDER BY p.creation_date DESC LIMIT ${limit.toLong}"
    ).query[Project]

  val selectReferenceAndStatus: Query0[(Project.Reference, GithubStatus)] =
    selectRequest(table, referenceFields ++ githubStatusFields)

  val selectProjects: Query0[Project] =
    selectRequest(fullTable, allFields)
}
