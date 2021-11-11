package ch.epfl.scala.services.storage.sql.tables

import java.time.Instant

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object ProjectTable {
  private val _ = documentationLinksMeta
  private val table = "projects"
  private val fields = Seq(
    "organization",
    "repository",
    "created_at",
    "esId"
  )

  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: NewProject): Fragment =
    fr0"${p.organization}, ${p.repository}, ${p.created}, ${p.esId}"

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

  def updateCreated(): Update[(Instant, NewProject.Reference)] =
    Update[(Instant, NewProject.Reference)](s"UPDATE $table SET created_at=? WHERE organization=? AND repository=?")

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectOne(ref: NewProject.Reference): doobie.ConnectionIO[Option[NewProject]] =
    selectOneQuery(ref).option

  def selectLatestProjects(limit: Int): doobie.Query0[NewProject] =
    buildSelect(
      tableFr,
      fr0"*",
      fr0"where created_at is not null" ++ space ++ fr0"ORDER BY created_at DESC" ++ space ++ fr0"LIMIT $limit"
    ).query[NewProject]

  def selectAllProjectRef(): doobie.Query0[NewProject.Reference] =
    buildSelect(tableFr, fr0"organization, repository").query[NewProject.Reference]

  private[tables] def selectOneQuery(ref: NewProject.Reference): doobie.Query0[NewProject] =
    buildSelect(tableFr, fieldsFr, where(ref)).query[NewProject]

}
