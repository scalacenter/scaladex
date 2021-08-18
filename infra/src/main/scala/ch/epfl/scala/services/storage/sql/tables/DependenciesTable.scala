package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildInsert
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildSelect
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object DependenciesTable {
  private val _ =
    dependencyWriter // for intellij not remove DoobieUtils.Mappings import
  private val table = "dependencies"
  private val fields = Seq(
    "source_groupId",
    "source_artifactId",
    "source_version",
    "target_groupId",
    "target_artifactId",
    "target_version",
    "scope"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(d: NewDependency): Fragment =
    fr0"${d.source.groupId}, ${d.source.artifactId} ,${d.source.version}, ${d.target.groupId}," ++
      fr0" ${d.target.artifactId}, ${d.target.version}, ${d.scope}"

  def insert(elt: NewDependency): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: Seq[NewDependency]): doobie.ConnectionIO[Int] =
    Update[NewDependency](insert(elts.head).sql).updateMany(elts)

  def indexedDependencies(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]
}
