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

  val getDirectDependencies: Query[(Project.Reference, SemanticVersion), ReleaseDependency.Direct] =
    selectRequest1[(Project.Reference, SemanticVersion, Project.Reference), ReleaseDependency.Direct](
      table,
      s"target_organization, target_repository, target_version, $scope",
      where = Some(
        "source_organization=? AND source_repository=? AND source_version=? AND NOT target_organization=? AND NOT target_repository=?"
      ),
      groupBy = Seq("target_organization", "target_repository", "target_version", scope)
    ).contramap { case (ref, v) => (ref, v, ref) }

  val getReverseDependencies: Query[Project.Reference, ReleaseDependency.Reverse] =
    Query[(Project.Reference, Project.Reference), ReleaseDependency.Reverse](
      """|SELECT DISTINCT organization, repository, target_version, scope
         |FROM release_dependencies rd
         |JOIN (
         |	SELECT a1.organization, a1.repository, MAX(a1.version) AS version
         |	FROM releases as a1
         |	JOIN (
         |		SELECT organization, repository, MAX(release_date) AS release_date
         |		FROM releases
         |		GROUP BY organization, repository
         |	) AS a2
         |	ON a1.organization=a2.organization AND a1.repository=a2.repository AND a1.release_date=a2.release_date
         |	GROUP BY a1.organization, a1.repository
         |) AS r
         |ON source_organization=organization AND source_repository=repository AND source_version=version
         |WHERE target_organization=? AND target_repository=? AND NOT source_organization=? AND NOT source_repository=?
         |""".stripMargin
    ).contramap(ref => (ref, ref))
}
