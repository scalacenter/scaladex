package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.newModel.NewProject
import doobie.util.fragment.Fragment
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import cats.data.NonEmptyList
import doobie.util.update.Update
import doobie.implicits._

object ProjectTable {
  private val table = "projects"
  private val fields = Seq("organization", "repository")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: NewProject): Fragment =
    fr0"${p.organization}, ${p.repository}"

  def insert(elt: NewProject): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update
  def insertMany(elts: NonEmptyList[NewProject]): doobie.ConnectionIO[Int] =
    Update[NewProject](insert(elts.head).sql).updateMany(elts)

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

}
