package scaladex.infra.sql

import scaladex.core.model.Scala3MigrationInsight
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*

object Scala3MigrationTable:
  val table: String = "scala3_migration_insights"

  val fields: Seq[String] = Seq("migrated", "project_count")

  // A project counts as migrated if ANY of its latest artifacts targets Scala 3 - it may still also
  // publish Scala 2.x artifacts for other modules, this is "has it started", not "is it done".
  val computeProjectCounts: Query0[Scala3MigrationInsight] =
    Query0[(Boolean, Long)](
      s"""|WITH project_scala3 AS (
          |  SELECT organization, repository, bool_or(language_version = '3') AS migrated
          |  FROM ${ArtifactTable.table}
          |  WHERE ${ArtifactTable.isLatestVersion} AND language_version != 'java'
          |  GROUP BY organization, repository
          |)
          |SELECT migrated, COUNT(*) FROM project_scala3 GROUP BY migrated""".stripMargin
    ).map(Scala3MigrationInsight.apply)

  val selectAll: Query0[Scala3MigrationInsight] =
    selectRequest[(Boolean, Long)](table, fields).map(Scala3MigrationInsight.apply)

  // The job always deletes everything and reinserts a fresh snapshot, so a plain insert is enough.
  val insert: Update[Scala3MigrationInsight] =
    insertRequest[(Boolean, Long)](table, fields).contramap(i => (i.migrated, i.projectCount))

  val deleteAll: Update[Unit] = Update[Unit](s"DELETE FROM $table")
end Scala3MigrationTable
