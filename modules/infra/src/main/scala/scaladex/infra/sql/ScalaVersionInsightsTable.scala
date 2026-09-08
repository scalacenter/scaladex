package scaladex.infra.sql

import scaladex.core.model.InsightsGranularity
import scaladex.core.model.Language
import scaladex.core.model.Scala
import scaladex.core.model.ScalaVersionInsight
import scaladex.core.model.Version
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*

object ScalaVersionInsightsTable:
  val table: String = "scala_version_insights"

  val fields: Seq[String] = Seq("kind", "language_version", "project_count")

  // Scala 3.x is a single binary version (language_version is always "3"), unlike 2.x where every
  // minor is its own binary version. For the "Minor" granularity we bucket 3.x by the minor of
  // full_scala_version instead, the exact compiler version a release was built with, e.g. "3.3.4" -> "3.3".
  private val minorVersionBucket: String =
    """CASE
      |  WHEN language_version = '3' AND full_scala_version IS NOT NULL
      |    THEN regexp_replace(full_scala_version, '^(\d+\.\d+).*$', '\1')
      |  ELSE language_version
      |END""".stripMargin

  private val excludeJava = Seq(ArtifactTable.isLatestVersion, "language_version != 'java'")

  // Counted from artifacts.is_latest_version, so it reflects the current state of the ecosystem,
  // not every version ever published. Java artifacts are excluded; this is a Scala-version breakdown.
  val computeBinaryCompatCounts: Query0[ScalaVersionInsight] =
    selectRequest[(Language, Long)](
      ArtifactTable.table,
      Seq("language_version", "COUNT(DISTINCT (organization, repository))"),
      where = excludeJava,
      groupBy = Seq("language_version")
    ).map { case (language, count) => ScalaVersionInsight(InsightsGranularity.BinaryCompat, language, count) }

  val computeMinorVersionCounts: Query0[ScalaVersionInsight] =
    selectRequest[(String, Long)](
      ArtifactTable.table,
      Seq(s"$minorVersionBucket AS version_bucket", "COUNT(DISTINCT (organization, repository))"),
      where = excludeJava,
      groupBy = Seq(minorVersionBucket)
    ).map {
      case (bucket, count) =>
        ScalaVersionInsight(InsightsGranularity.Minor, Language.parse(bucket).getOrElse(Scala(Version(3))), count)
    }

  val selectAll: Query0[ScalaVersionInsight] =
    selectRequest[(InsightsGranularity, Language, Long)](table, fields).map(ScalaVersionInsight.apply)

  // The job always deletes everything and reinserts a fresh snapshot, so a plain insert is enough.
  val insert: Update[ScalaVersionInsight] =
    insertRequest[(InsightsGranularity, Language, Long)](table, fields)
      .contramap(i => (i.granularity, i.language, i.projectCount))

  val deleteAll: Update[Unit] = Update[Unit](s"DELETE FROM $table")
end ScalaVersionInsightsTable
