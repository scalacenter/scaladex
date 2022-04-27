package scaladex.infra.sql

import doobie._
import doobie.util.update.Update
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.model.ReleaseDependency
import scaladex.core.model.SemanticVersion
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

object ArtifactDependencyTable {
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
    fields.map("d." + _) ++
      ArtifactTable.fields.map("a." + _)

  val insertIfNotExist: Update[ArtifactDependency] =
    insertOrUpdateRequest(table, fields, fields)

  val count: doobie.Query0[Long] =
    selectRequest(table, Seq("COUNT(*)"))

  val select: Query[Artifact.MavenReference, ArtifactDependency] =
    selectRequest(table, fields, sourceFields)

  val selectDirectDependency: doobie.Query[Artifact.MavenReference, ArtifactDependency.Direct] =
    selectRequest(
      tableWithTargetArtifact,
      dependencyAndArtifactFields,
      sourceFields.map(f => s"d.$f")
    )

  val selectReverseDependency: Query[Artifact.MavenReference, ArtifactDependency.Reverse] =
    selectRequest(
      tableWithSourceArtifact,
      dependencyAndArtifactFields,
      targetFields.map(f => s"d.$f")
    )

  val computeProjectDependencies: Query[(Project.Reference, SemanticVersion), ProjectDependency] =
    selectRequest1[(Project.Reference, SemanticVersion, Project.Reference), ProjectDependency](
      fullJoin,
      "d.organization, d.repository, d.version, t.organization, t.repository, t.version, d.scope",
      where = Some("d.organization=? AND d.repository=? AND d.version=? AND (t.organization<>? OR t.repository<>?)"),
      groupBy =
        Seq("d.organization", "d.repository", "d.version", "t.organization", "t.repository", "t.version", "d.scope")
    ).contramap { case (ref, version) => (ref, version, ref) }

  val computeReleaseDependency: Query0[ReleaseDependency] = {
    val sourceReleaseFields = ReleaseTable.primaryKeys.map("d." + _)
    val targetReleaseFields = ReleaseTable.primaryKeys.map("t." + _)
    selectRequest(
      fullJoin,
      s"${sourceReleaseFields.mkString(", ")}, MIN(d.release_date), ${targetReleaseFields.mkString(", ")}, MIN(t.release_date), d.scope",
      groupBy = (sourceReleaseFields ++ targetReleaseFields) ++ Seq("d.scope")
    )
  }

  val selectDependencyFromProject: Query[Project.Reference, ArtifactDependency] =
    selectRequest(
      tableWithSourceArtifact,
      fields,
      Seq("a.organization", "a.repository")
    )
}
