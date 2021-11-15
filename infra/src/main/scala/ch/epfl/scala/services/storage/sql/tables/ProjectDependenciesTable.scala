package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildInsertOrUpdate
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildSelect
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import ch.epfl.scala.utils.DoobieUtils.Mappings.dependencyWriter
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment

object ProjectDependenciesTable {
  private val _ = dependencyWriter // for intellij not remove DoobieUtils.Mappings import
  private val fields = Seq(
    "source_organization",
    "source_repository",
    "target_organization",
    "target_repository"
  )
  val table: Fragment = Fragment.const0("project_dependencies")
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(p: ProjectDependency): Fragment =
    fr0"${p.source.organization}, ${p.source.repository}, ${p.target.organization}, ${p.target.repository}"

  private[tables] def insertOrUpdate(p: ProjectDependency): Update0 = {
    val onConflict =
      fr0"source_organization, source_repository, target_organization, target_repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      table,
      fieldsFr,
      values(p),
      onConflict,
      doAction
    ).update
  }

  private def whereTarget(projectRef: NewProject.Reference): Fragment =
    fr0"WHERE target_organization=${projectRef.organization} AND target_repository=${projectRef.repository}"

  def insertMany(
      p: Seq[ProjectDependency]
  ): ConnectionIO[Int] =
    Update[ProjectDependency](insertOrUpdate(p.head).sql).updateMany(p)

  def countInverseDependencies(projectRef: NewProject.Reference): ConnectionIO[Int] =
    buildSelect(table, fr0"count(*)", whereTarget(projectRef)).query[Int].unique

  def getMostDependentUponProjects(
      max: Int
  ): Query0[(NewProject.Reference, Long)] =
    buildSelect(
      table,
      fr0"target_organization, target_repository, Count(DISTINCT (source_organization, source_repository)) as total",
      fr0"GROUP BY target_organization, target_repository" ++
        fr0" ORDER BY total DESC" ++
        fr0" LIMIT ${max.toLong}"
    ).query[(NewProject.Reference, Long)]
}
