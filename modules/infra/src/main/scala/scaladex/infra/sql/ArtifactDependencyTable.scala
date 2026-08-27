package scaladex.infra.sql

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.Version
import scaladex.infra.sql.DoobieMappings.given
import scaladex.infra.sql.DoobieUtils.*

import doobie.*
import doobie.util.update.Update

object ArtifactDependencyTable:
  val table = "artifact_dependencies"
  val sourceFields: Seq[String] = Seq("source_group_id", "source_artifact_id", "source_version")
  val targetFields: Seq[String] = Seq("target_group_id", "target_artifact_id", "target_version")
  val fields: Seq[String] = sourceFields ++ targetFields ++ Seq("scope")

  private val tableWithSourceArtifact =
    s"($table d " +
      s"INNER JOIN ${ArtifactTable.table} a ON " +
      s"d.source_group_id = a.group_id AND " +
      s"d.source_artifact_id = a.artifact_id AND " +
      s"d.source_version = a.version)"

  private val tableWithTargetArtifact =
    s"($table d " +
      s"LEFT JOIN ${ArtifactTable.table} a ON " +
      s"d.target_group_id = a.group_id AND " +
      s"d.target_artifact_id = a.artifact_id AND " +
      s"d.target_version = a.version)"

  private val fullJoin =
    s"($tableWithSourceArtifact d " +
      s"INNER JOIN ${ArtifactTable.table} t ON " +
      s"d.target_group_id = t.group_id AND " +
      s"d.target_artifact_id = t.artifact_id AND " +
      s"d.target_version = t.version)"

  private val dependencyAndArtifactFields =
    fields.map("d." + _) ++ ArtifactTable.mainFields.map("a." + _)

  val insertIfNotExist: Update[ArtifactDependency] =
    insertOrUpdateRequest(table, fields, fields)

  val count: doobie.Query0[Long] =
    selectRequest(table, Seq("COUNT(*)"))

  val select: Query[Artifact.Reference, ArtifactDependency] =
    selectRequest(table, fields, sourceFields)

  val selectDirectDependency: doobie.Query[Artifact.Reference, ArtifactDependency.Direct] =
    selectRequest(
      tableWithTargetArtifact,
      dependencyAndArtifactFields,
      sourceFields.map(f => s"d.$f")
    )

  val selectReverseDependency: Query[Artifact.Reference, ArtifactDependency.Reverse] =
    selectRequest(
      tableWithSourceArtifact,
      dependencyAndArtifactFields,
      targetFields.map(f => s"d.$f")
    )

  // deduped and limited before the artifacts join, so the join runs only for the kept rows
  val selectReverseDependencyPage: Query[(Artifact.Reference, Long, Long), ArtifactDependency.Reverse] =
    val depFields = fields.mkString(", ")
    val outFields = dependencyAndArtifactFields.mkString(", ")
    Query(
      s"""|SELECT $outFields
          |FROM (
          |  SELECT DISTINCT ON (source_group_id, source_artifact_id) $depFields
          |  FROM $table
          |  WHERE target_group_id = ? AND target_artifact_id = ? AND target_version = ?
          |  ORDER BY source_group_id, source_artifact_id, source_version
          |  LIMIT ? OFFSET ?
          |) d
          |INNER JOIN ${ArtifactTable.table} a
          |  ON d.source_group_id = a.group_id AND d.source_artifact_id = a.artifact_id AND d.source_version = a.version""".stripMargin
    )
  end selectReverseDependencyPage

  val countReverseDependency: Query[Artifact.Reference, Long] =
    selectRequest[Artifact.Reference, Long](
      table,
      Seq("COUNT(DISTINCT (source_group_id, source_artifact_id))"),
      targetFields
    )

  val computeProjectDependencies: Query[(Project.Reference, Version), ProjectDependency] =
    selectRequest1[(Project.Reference, Version, Project.Reference), ProjectDependency](
      fullJoin,
      Seq("d.organization", "d.repository", "d.version", "t.organization", "t.repository", "t.version", "d.scope"),
      where = Seq("d.organization=?", "d.repository=?", "d.version=?", "(t.organization<>? OR t.repository<>?)"),
      groupBy =
        Seq("d.organization", "d.repository", "d.version", "t.organization", "t.repository", "t.version", "d.scope")
    ).contramap { case (ref, version) => (ref, version, ref) }

  val selectDependencyFromProject: Query[Project.Reference, ArtifactDependency] =
    selectRequest(
      tableWithSourceArtifact,
      fields,
      Seq("a.organization", "a.repository")
    )
end ArtifactDependencyTable
