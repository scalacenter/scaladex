package scaladex.infra.storage.sql.tables

import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.infra.util.DoobieUtils.Fragments._
import scaladex.infra.util.DoobieUtils.Mappings._

object ProjectDependenciesTable {
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

  private def whereTarget(projectRef: Project.Reference): Fragment =
    fr0"WHERE target_organization=${projectRef.organization} AND target_repository=${projectRef.repository}"

  def insertMany(
      p: Seq[ProjectDependency]
  ): ConnectionIO[Int] =
    Update[ProjectDependency](insertOrUpdate(p.head).sql).updateMany(p)

  def countInverseDependencies(projectRef: Project.Reference): ConnectionIO[Int] =
    buildSelect(table, fr0"count(*)", whereTarget(projectRef)).query[Int].unique

  def getMostDependentUponProjects(
      max: Int
  ): Query0[(Project.Reference, Long)] =
    buildSelect(
      table,
      fr0"target_organization, target_repository, Count(DISTINCT (source_organization, source_repository)) as total",
      fr0"GROUP BY target_organization, target_repository" ++
        fr0" ORDER BY total DESC" ++
        fr0" LIMIT ${max.toLong}"
    ).query[(Project.Reference, Long)]
}
