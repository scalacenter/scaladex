package scaladex.infra.storage.sql.tables

import doobie._
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.infra.util.DoobieUtils.Mappings._
import scaladex.infra.util.DoobieUtils._

object ProjectDependenciesTable {
  val table: String = "project_dependencies"

  val sourceFields: Seq[String] = Seq("source_organization", "source_repository")
  val targetFields: Seq[String] = Seq("target_organization", "target_repository")
  val fields: Seq[String] = sourceFields ++ targetFields

  val insertOrUpdate: Update[ProjectDependency] =
    insertOrUpdateRequest(table, fields, fields)

  val countInverseDependencies: Query[Project.Reference, Int] =
    selectRequest(table, Seq("COUNT(*)"), targetFields)

  def getMostDependentUponProjects(limit: Long): Query0[(Project.Reference, Long)] = {
    val total = s"COUNT(DISTINCT(${sourceFields.mkString(", ")})) AS total"
    selectRequest(
      table,
      (targetFields :+ total).mkString(", "),
      groupBy = targetFields,
      orderBy = Some("total DESC"),
      limit = Some(limit)
    )
  }
  val deleteSourceProject: Update[Project.Reference] =
    deleteRequest(table, sourceFields)

  val deleteTargetProject: Update[Project.Reference] =
    deleteRequest(table, targetFields)

}
