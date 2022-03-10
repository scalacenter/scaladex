package scaladex.infra.sql

import doobie._
import doobie.util.update.Update
import scaladex.core.model.Project
import scaladex.core.model.ReleaseDependency
import scaladex.core.model.SemanticVersion
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._
import scaladex.infra.sql.DoobieUtils.insertOrUpdateRequest

object ReleaseDependenciesTable {
  private val table: String = "release_dependencies"
  private val sourcePrimaryKeys: Seq[String] =
    Seq(
      "source_organization",
      "source_repository",
      "source_platform",
      "source_language_version",
      "source_version"
    )
  private val targetPrimaryKeys: Seq[String] =
    Seq(
      "target_organization",
      "target_repository",
      "target_platform",
      "target_language_version",
      "target_version"
    )

  val scope = "scope"
  val sourceKeys: Seq[String] = sourcePrimaryKeys :+ "source_release_date"
  val targetKeys: Seq[String] = targetPrimaryKeys :+ "target_release_date"
  private val primaryKeys = (sourcePrimaryKeys ++ targetPrimaryKeys) :+ scope

  private val fields: Seq[String] = (sourceKeys ++ targetKeys) :+ scope

  val insertIfNotExists: Update[ReleaseDependency] =
    insertOrUpdateRequest(table, fields, primaryKeys)

  val getDirectDependencies: Query[(Project.Reference, SemanticVersion), ReleaseDependency.Result] =
    selectRequest1(
      table,
      s"target_organization, target_repository, target_version, $scope, MIN(source_release_date)",
      where = Some("source_organization=? AND source_repository=? AND source_version=?"),
      groupBy = Seq("target_organization", "target_repository", "target_version", scope)
    )

  val getReverseDependencies: Query[(Project.Reference, SemanticVersion), ReleaseDependency.Result] =
    selectRequest1(
      table,
      s"source_organization, source_repository, source_version, $scope, MIN(source_release_date)",
      where = Some("target_organization=? AND target_repository=? AND target_version=?"),
      groupBy = Seq("source_organization", "source_repository", "source_version", scope)
    )
}
