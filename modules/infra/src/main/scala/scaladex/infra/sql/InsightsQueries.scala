package scaladex.infra.sql

import scaladex.core.model.InsightsGranularity
import scaladex.core.model.Language
import scaladex.core.model.Scala
import scaladex.core.model.Scala3MigrationInsight
import scaladex.core.model.ScalaVersionInsight
import scaladex.core.model.Version
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*

// Aggregate stats over the artifacts table, not backed by their own table: they're computed on
// demand and cached rather than persisted (see SqlDatabase.scalaVersionInsightsCache).
object InsightsQueries:

  private val excludeJava = Seq(ArtifactTable.isLatestVersion, "language_version != 'java'")

  // Scala 3.x is a single binary version (language_version is always "3"), unlike 2.x where every
  // minor is its own binary version. For the "Minor" granularity we bucket 3.x by the minor of
  // full_scala_version instead, the exact compiler version a release was built with, e.g. "3.3.4" -> "3.3".
  private val minorVersionBucket: String =
    """CASE
      |  WHEN language_version = '3' AND full_scala_version IS NOT NULL
      |    THEN regexp_replace(full_scala_version, '^(\d+\.\d+).*$', '\1')
      |  ELSE language_version
      |END""".stripMargin

  // Counted from artifacts.is_latest_version, so it reflects the current state of the ecosystem,
  // not every version ever published. Java artifacts are excluded; this is a Scala-version breakdown.
  val computeBinaryCompatCounts: Query0[ScalaVersionInsight] =
    selectRequest[(Language, Long)](
      ArtifactTable.table,
      Seq("language_version", "COUNT(DISTINCT (organization, repository))"),
      where = excludeJava,
      groupBy = Seq("language_version")
    ).map { (language, count) => ScalaVersionInsight(InsightsGranularity.BinaryCompat, language, count) }

  val computeMinorVersionCounts: Query0[ScalaVersionInsight] =
    selectRequest[(String, Long)](
      ArtifactTable.table,
      Seq(s"$minorVersionBucket AS version_bucket", "COUNT(DISTINCT (organization, repository))"),
      where = excludeJava,
      groupBy = Seq(minorVersionBucket)
    ).map { (bucket, count) =>
      ScalaVersionInsight(InsightsGranularity.Minor, Language.parse(bucket).getOrElse(Scala(Version(3))), count)
    }

  // A project counts as migrated if any of its latest artifacts targets Scala 3. It may still also
  // publish Scala 2.x artifacts for other modules; this tracks "has it started", not "is it done".
  val computeScala3MigrationCounts: Query0[Scala3MigrationInsight] =
    Query0[(Boolean, Long)](
      s"""|WITH project_scala3 AS (
          |  SELECT organization, repository, bool_or(language_version = '3') AS migrated
          |  FROM ${ArtifactTable.table}
          |  WHERE ${ArtifactTable.isLatestVersion} AND language_version != 'java'
          |  GROUP BY organization, repository
          |)
          |SELECT migrated, COUNT(*) FROM project_scala3 GROUP BY migrated""".stripMargin
    ).map(Scala3MigrationInsight.apply)
end InsightsQueries
