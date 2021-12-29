package scaladex.infra.storage.sql.tables

import java.time.Instant

import doobie._
import doobie.util.update.Update
import scaladex.core.model.Artifact
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.infra.util.DoobieUtils.Mappings._
import scaladex.infra.util.DoobieUtils._

object ArtifactTable {
  private[sql] val table = "artifacts"

  val mavenReferenceFields: Seq[String] = Seq("group_id", "artifact_id", "version")
  val projectReferenceFields: Seq[String] = Seq("organization", "repository")

  private[sql] val fields = mavenReferenceFields ++
    Seq("artifact_name", "platform") ++
    projectReferenceFields ++
    Seq(
      "description",
      "release_date",
      "resolver",
      "licenses",
      "is_non_standard_Lib"
    )

  val insert: Update[Artifact] = insertRequest(table, fields)

  val count: Query0[Long] =
    selectRequest(table, "COUNT(*)")

  val updateProjectRef: Update[(Project.Reference, Artifact.MavenReference)] =
    updateRequest(table, projectReferenceFields, mavenReferenceFields)

  val selectArtifactByProject: Query[Project.Reference, Artifact] =
    selectRequest(table, Seq("*"), projectReferenceFields)

  val selectArtifactByProjectAndName: Query[(Project.Reference, Artifact.Name), Artifact] =
    selectRequest(table, Seq("*"), projectReferenceFields :+ "artifact_name")

  val selectPlatform: Query0[(Project.Organization, Project.Repository, Platform)] =
    selectRequest(
      table,
      "organization, repository, platform",
      groupBy = Seq("organization", "repository", "platform")
    )

  val selectOldestByProject: Query0[(Instant, Project.Reference)] =
    selectRequest(
      table,
      "MIN(release_date) as oldest_artifact, organization, repository",
      where = Some("release_date IS NOT NULL"),
      groupBy = projectReferenceFields
    )

}
