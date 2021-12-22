package ch.epfl.scala.services.storage.sql.tables

import java.time.Instant

import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import ch.epfl.scala.utils.DoobieUtils._
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object ProjectTable {
  private val _ = documentationLinksMeta

  private val table: String = "projects"
  private val tableFr: Fragment = Fragment.const0(table)
  private val fields: Seq[String] = Seq("organization", "repository", "created_at", "github_status")
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val orgaAndRepo: String = "organization, repository"

  private def values(p: Project): Fragment =
    fr0"${p.organization}, ${p.repository}, ${p.created}, ${p.githubStatus}"

  private def values(ref: Project.Reference): Fragment =
    fr0"${ref.organization}, ${ref.repository}"

  private val allFields: Seq[String] = fields.map("p." + _) ++
    GithubInfoTable.fields.drop(2).map("g." + _) ++
    ProjectUserFormTable.fields.drop(2).map("f." + _)
  private val allFieldsFr: Fragment = Fragment.const0(allFields.mkString(", "))
  private val fullTable: Fragment =
    fr0"$tableFr p " ++
      fr0"LEFT JOIN ${GithubInfoTable.table} g ON p.organization = g.organization AND p.repository = g.repository " ++
      fr0"LEFT JOIN ${ProjectUserFormTable.table} f ON p.organization = f.organization AND p.repository = f.repository"

  val insertIfNotExists: Update[(Project.Reference, GithubStatus)] =
    insertOrUpdateRequest(
      table,
      Seq("organization", "repository", "github_status"),
      Seq("organization", "repository")
    )

  val updateCreated: Update[(Instant, Project.Reference)] =
    Update[(Instant, Project.Reference)](s"UPDATE $table SET created_at=? WHERE organization=? AND repository=?")

  val updateGithubStatus: Update[(GithubStatus, Project.Reference)] =
    Update[(GithubStatus, Project.Reference)](
      s"UPDATE $table SET github_status=? WHERE organization=? AND repository=?"
    )

  def indexedProjects(): Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectOne(ref: Project.Reference): Query0[Project] =
    buildSelect(
      fullTable,
      allFieldsFr,
      fr0"WHERE p.organization = ${ref.organization} AND p.repository = ${ref.repository}"
    ).query[Project]

  def selectLatestProjects(limit: Int): Query0[Project] =
    buildSelect(
      fullTable,
      allFieldsFr,
      fr0"WHERE created_at IS NOT NULL ORDER BY created_at DESC LIMIT $limit"
    ).query[Project]

  def selectAllProjectRef(): Query0[Project.Reference] =
    buildSelect(tableFr, fr0"organization, repository").query[Project.Reference]

  def selectAllProjects: Query0[Project] =
    buildSelect(fullTable, allFieldsFr).query[Project]
}
