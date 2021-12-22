package scaladex.infra.storage.sql.tables

import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.ProjectDependency
import scaladex.infra.util.DoobieUtils.Fragments._
import scaladex.infra.util.DoobieUtils.Mappings._
import scaladex.infra.util.DoobieUtils.insertRequest

object ArtifactDependencyTable {
  val _ = contributorMeta
  private[sql] val table = "artifact_dependencies"
  private[sql] val fields = Seq(
    "source_group_id",
    "source_artifact_id",
    "source_version",
    "target_group_id",
    "target_artifact_id",
    "target_version",
    "scope"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val tableWithSourceArtifact = Fragment.const0(
    s"$table d " +
      s"INNER JOIN ${ArtifactTable.table} a ON " +
      s"d.source_group_id = a.group_id AND " +
      s"d.source_artifact_id = a.artifact_id AND " +
      s"d.source_version = a.version"
  )

  private val tableWithTargetArtifact = Fragment.const0(
    s"$table d " +
      s"LEFT JOIN ${ArtifactTable.table} a ON " +
      s"d.target_group_id = a.group_id AND " +
      s"d.target_artifact_id = a.artifact_id AND " +
      s"d.target_version = a.version"
  )

  private val fullJoin =
    fr0"( " ++ tableWithSourceArtifact ++ fr0") d " ++
      Fragment.const0(
        s"INNER JOIN ${ArtifactTable.table} t ON " +
          s"d.target_group_id = t.group_id AND " +
          s"d.target_artifact_id = t.artifact_id AND " +
          s"d.target_version = t.version"
      )

  private val fieldstableWithArtifact = Fragment.const0(
    (
      fields.map("d." + _) ++
        ArtifactTable.fields.map("a." + _)
    ).mkString(", ")
  )

  val insert: Update[ArtifactDependency] = insertRequest(table, fields)

  def indexedDependencies(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def find(m: Artifact.MavenReference): doobie.Query0[ArtifactDependency] =
    buildSelect(
      tableFr,
      fieldsFr,
      fr0"WHERE source_group_id=${m.groupId} AND source_artifact_id=${m.artifactId} AND source_version=${m.version}"
    ).query[ArtifactDependency]

  def selectDirectDependencies(release: Artifact): doobie.Query0[ArtifactDependency.Direct] =
    buildSelect(
      tableWithTargetArtifact,
      fieldstableWithArtifact,
      fr0"WHERE d.source_group_id=${release.mavenReference.groupId}" ++
        fr0" AND d.source_artifact_id=${release.mavenReference.artifactId}" ++
        fr0" AND d.source_version=${release.mavenReference.version}"
    )
      .query[ArtifactDependency.Direct]

  def selectReverseDependencies(release: Artifact): doobie.Query0[ArtifactDependency.Reverse] =
    buildSelect(
      tableWithSourceArtifact,
      fieldstableWithArtifact,
      fr0"WHERE d.target_group_id=${release.mavenReference.groupId} AND" ++
        fr0" d.target_artifact_id=${release.mavenReference.artifactId}" ++
        fr0" AND d.target_version=${release.mavenReference.version}"
    )
      .query[ArtifactDependency.Reverse]

  def getAllProjectDependencies(): doobie.Query0[ProjectDependency] =
    buildSelect(
      fullJoin,
      fr0"DISTINCT d.organization, d.repository, t.organization, t.repository",
      fr0"GROUP BY d.organization, d.repository, t.organization, t.repository"
    ).query[ProjectDependency]
}
