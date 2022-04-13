package scaladex.infra.sql

import java.time.Instant

import doobie._
import doobie.util.update.Update
import scaladex.core.model.Artifact
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Release
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

object ArtifactTable {
  private[sql] val table = "artifacts"

  val mavenReferenceFields: Seq[String] = Seq("group_id", "artifact_id", "version")
  val projectReferenceFields: Seq[String] = Seq("organization", "repository")

  // Don't use `select *...` but `select fields`
  // the table have more fields than in Artifact instance
  private[sql] val fields = mavenReferenceFields ++
    Seq("artifact_name") ++
    projectReferenceFields ++
    Seq(
      "description",
      "release_date",
      "resolver",
      "licenses",
      "is_non_standard_Lib",
      "platform",
      "language_version"
    )
  // these field are usually excluded when we read artifacts from the artifacts table.
  val versionFields: Seq[String] = Seq("is_semantic", "is_prerelease")

  def insertIfNotExist(artifact: Artifact): ConnectionIO[Int] =
    insertIfNotExist.run((artifact, artifact.version.isSemantic, artifact.version.isPreRelease))

  def insertIfNotExist(artifacts: Seq[Artifact]): ConnectionIO[Int] =
    insertIfNotExist.updateMany(artifacts.map(a => (a, a.version.isSemantic, a.version.isPreRelease)))

  private[sql] val insertIfNotExist: Update[(Artifact, Boolean, Boolean)] =
    insertOrUpdateRequest(table, fields ++ versionFields, mavenReferenceFields)

  val count: Query0[Long] =
    selectRequest(table, "COUNT(*)")

  val updateProjectRef: Update[(Project.Reference, Artifact.MavenReference)] =
    updateRequest(table, projectReferenceFields, mavenReferenceFields)

  val updateReleaseDate: Update[(Instant, Artifact.MavenReference)] =
    updateRequest(table, Seq("release_date"), mavenReferenceFields)

  val selectAllArtifacts: Query0[Artifact] =
    selectRequest(table, fields = fields)

  val selectArtifactByLanguage: Query[Language, Artifact] =
    selectRequest(table, fields, keys = Seq("language_version"))

  val selectArtifactByPlatform: Query[Platform, Artifact] =
    selectRequest(table, fields, keys = Seq("platform"))

  val selectArtifactByLanguageAndPlatform: Query[(Language, Platform), Artifact] =
    selectRequest(table, fields, keys = Seq("language_version", "platform"))

  val selectArtifactByProject: Query[Project.Reference, Artifact] = {
    val where = projectReferenceFields.map(f => s"$f=?").mkString(" AND ")
    selectRequest1(
      table,
      fields.mkString(", "),
      where = Some(where),
      orderBy = Some("release_date DESC"),
      limit = Some(10000)
    )
  }

  val selectArtifactByProjectAndName: Query[(Project.Reference, Artifact.Name), Artifact] =
    selectRequest(table, fields, projectReferenceFields :+ "artifact_name")

  val selectByMavenReference: Query[Artifact.MavenReference, Artifact] =
    selectRequest(table, fields, mavenReferenceFields)

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

  val getReleasesFromArtifacts: Query0[Release] =
    selectRequest(
      table,
      "organization, repository, platform , language_version , version , MIN(release_date) as release_date",
      groupBy = Seq("organization", "repository ", "platform ", "language_version", "version")
    )

}
