package scaladex.infra.sql

import doobie._
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.Version
import scaladex.infra.sql.DoobieMappings._
import scaladex.infra.sql.DoobieUtils._

object ProjectDependenciesTable {
  val table: String = "project_dependencies"

  val sourceFields: Seq[String] = Seq("source_organization", "source_repository", "source_version")
  val targetFields: Seq[String] = Seq("target_organization", "target_repository", "target_version")
  val allFields: Seq[String] = sourceFields ++ targetFields :+ "scope"

  val insertOrUpdate: Update[ProjectDependency] =
    insertOrUpdateRequest(table, allFields, allFields)

  val countDependents: Query[Project.Reference, Long] =
    selectRequest(
      table,
      Seq("COUNT(DISTINCT (source_organization, source_repository))"),
      Seq("target_organization", "target_repository")
    )

  val getDependents: Query[Project.Reference, ProjectDependency] =
    selectRequest(table, allFields, Seq("target_organization", "target_repository"))

  val getDependencies: Query[(Project.Reference, Version), ProjectDependency] =
    selectRequest(table, allFields, sourceFields)

  val deleteBySource: Update[Project.Reference] =
    deleteRequest(table, Seq("source_organization", "source_repository"))
}
