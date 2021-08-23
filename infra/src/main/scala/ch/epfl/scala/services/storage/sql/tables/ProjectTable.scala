package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment

object ProjectTable {
  private val _ = documentationLinksMeta
  private val table = "projects"
  private val fields = Seq(
    "organization",
    "repository",
    "esId"
  )

  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: NewProject): Fragment =
    fr0"${p.organization}, ${p.repository}, ${p.esId}"

  def insert(elt: NewProject): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertOrUpdate(elt: NewProject): doobie.Update0 = {
    val onConflict = fr0"organization, repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      tableFr,
      fieldsFr,
      values(elt),
      onConflict,
      doAction
    ).update
  }

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectOne(
      org: Organization,
      repo: Repository
  ): doobie.ConnectionIO[Option[NewProject]] =
    selectOneQuery(org, repo).option

  private[tables] def selectOneQuery(
      org: Organization,
      repo: Repository
  ): doobie.Query0[NewProject] =
    buildSelect(
      tableFr,
      fieldsFr,
      where(org, repo)
    ).query[NewProject]

}
