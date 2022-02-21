package scaladex.infra.sql

import java.time.Instant

import doobie._
import doobie.util.update.Update
import scaladex.core.model.Artifact
import scaladex.core.model.Project
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

object ArtifactTable {
  private[sql] val table = "artifacts"

  val mavenReferenceFields: Seq[String] = Seq("group_id", "artifact_id", "version")
  val projectReferenceFields: Seq[String] = Seq("organization", "repository")

  private[sql] val fields = mavenReferenceFields ++
    Seq("artifact_name", "binary_version") ++
    projectReferenceFields ++
    Seq(
      "description",
      "release_date",
      "resolver",
      "licenses",
      "is_non_standard_Lib"
    )

  val insertIfNotExist: Update[Artifact] =
    insertOrUpdateRequest(table, fields, mavenReferenceFields)

  val count: Query0[Long] =
    selectRequest(table, "COUNT(*)")

  val updateProjectRef: Update[(Project.Reference, Artifact.MavenReference)] =
    updateRequest(table, projectReferenceFields, mavenReferenceFields)

  val selectArtifactByProject: Query[Project.Reference, Artifact] =
    selectRequest(table, Seq("*"), projectReferenceFields)

  val selectArtifactByProjectAndName: Query[(Project.Reference, Artifact.Name), Artifact] =
    selectRequest(table, Seq("*"), projectReferenceFields :+ "artifact_name")

  val selectGroupIds: Query0[Artifact.GroupId] =
    selectRequest(table, "DISTINCT group_id")

  val selectMavenReference: Query0[Artifact.MavenReference] =
    selectRequest(table, """DISTINCT group_id, artifact_id, "version"""")

  val selectOldestByProject: Query0[(Instant, Project.Reference)] =
    selectRequest(
      table,
      "MIN(release_date) as oldest_artifact, organization, repository",
      where = Some("release_date IS NOT NULL"),
      groupBy = projectReferenceFields
    )

}
