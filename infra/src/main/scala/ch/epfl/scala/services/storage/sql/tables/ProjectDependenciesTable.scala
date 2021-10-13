package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildInsertOrUpdate
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildSelect
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import ch.epfl.scala.utils.DoobieUtils.Mappings.dependencyWriter
import doobie.Update
import doobie.implicits._
import doobie.util.fragment.Fragment

object ProjectDependenciesTable {
  private val _ =
    dependencyWriter // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "project_dependencies"
  private[sql] val fields = Seq(
    "source_organization",
    "source_repository",
    "target_organization",
    "target_repository"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(p: ProjectDependency): Fragment =
    fr0"${p.source.organization}, ${p.source.organization}, ${p.target.organization}, ${p.target.organization}"

  private[tables] def insertOrUpdate(p: ProjectDependency): doobie.Update0 = {
    val onConflict =
      fr0"source_organization, source_repository, target_organization, target_repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      tableFr,
      fieldsFr,
      values(p),
      onConflict,
      doAction
    ).update
  }

  def insertMany(
      p: Seq[ProjectDependency]
  ): doobie.ConnectionIO[Int] =
    Update[ProjectDependency](insertOrUpdate(p.head).sql).updateMany(p)

  def getMostDependentUponProjects(
      max: Int
  ): doobie.Query0[(NewProject.Reference, Long)] =
    buildSelect(
      tableFr,
      fr0"target_organization, target_repository, Count(DISTINCT (source_organization, source_repository)) as total",
      fr0"GROUP BY target_organization, target_repository" ++
        fr0" ORDER BY total DESC" ++
        fr0" LIMIT ${max.toLong}"
    ).query[(NewProject.Reference, Long)]
}
