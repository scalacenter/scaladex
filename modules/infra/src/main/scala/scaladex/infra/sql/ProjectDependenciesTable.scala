package scaladex.infra.sql

import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.Version
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*

object ProjectDependenciesTable:
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

  // the first N distinct source projects are selected before fetching their rows
  val getReverseDependenciesPage: Query[(Project.Reference, Long, Long), ProjectDependency] =
    val outFields = allFields.map(f => s"d.$f").mkString(", ")
    Query[(Project.Reference, Long, Long, Project.Reference), ProjectDependency](
      s"""|SELECT $outFields
          |FROM (
          |  SELECT DISTINCT source_organization, source_repository
          |  FROM $table
          |  WHERE target_organization = ? AND target_repository = ?
          |  ORDER BY source_organization, source_repository
          |  LIMIT ? OFFSET ?
          |) s
          |INNER JOIN $table d
          |  ON d.source_organization = s.source_organization
          |  AND d.source_repository = s.source_repository
          |  AND d.target_organization = ? AND d.target_repository = ?""".stripMargin
    ).contramap { case (ref, limit, offset) => (ref, limit, offset, ref) }
  end getReverseDependenciesPage

  val getDependencies: Query[(Project.Reference, Version), ProjectDependency] =
    selectRequest(table, allFields, sourceFields)

  val deleteBySource: Update[Project.Reference] =
    deleteRequest(table, Seq("source_organization", "source_repository"))
end ProjectDependenciesTable
