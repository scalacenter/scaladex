package scaladex.infra.sql

import java.time.Instant

import doobie._
import doobie.util.update.Update
import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Release
import scaladex.core.model.SemanticVersion
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

  val countVersionsByProjct: Query[Project.Reference, Long] =
    selectRequest(table, Seq("Count(DISTINCT version)"), projectReferenceFields)

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

  val selectArtifactBy: Query[(Project.Reference, Artifact.Name, SemanticVersion), Artifact] =
    selectRequest1(
      table,
      fields.mkString(", "),
      where = Some("organization=? AND repository=? AND artifact_name=? AND version=?")
    )

  def selectArtifactByParams(
      binaryVersions: Seq[BinaryVersion],
      preReleases: Boolean
  ): Query[(Project.Reference, Artifact.Name), Artifact] = {
    val binaryVersionFilter =
      if (binaryVersions.isEmpty) "true"
      else
        binaryVersions
          .map(bv => s"(platform='${bv.platform.label}' AND language_version='${bv.language.label}')")
          .mkString("(", " OR ", ")")
    val preReleaseFilter = if (preReleases) s"true" else "is_prerelease=false"
    val releases =
      s"""|SELECT organization, repository, artifact_name, version
          |FROM $table WHERE
          |organization=? AND repository=? AND artifact_name=? AND $binaryVersionFilter AND $preReleaseFilter
          |""".stripMargin
    val artifactFields = fields.map(f => s"a.$f").mkString(", ")
    Query[(Project.Reference, Artifact.Name), Artifact](
      s"""|SELECT $artifactFields FROM ($releases) r INNER JOIN $table a ON
          | r.version = a.version AND r.organization = a.organization AND
          | r.repository = a.repository AND r.artifact_name = a.artifact_name""".stripMargin
    )
  }

  val selectArtifactByProjectAndName: Query[(Project.Reference, Artifact.Name), Artifact] =
    selectRequest(table, fields, projectReferenceFields :+ "artifact_name")

  val selectArtifactByProjectAndVersion: Query[(Project.Reference, SemanticVersion), Artifact] =
    selectRequest(table, fields, projectReferenceFields :+ "version")

  val selectByMavenReference: Query[Artifact.MavenReference, Artifact] =
    selectRequest(table, fields, mavenReferenceFields)

  val selectGroupIds: Query0[Artifact.GroupId] =
    selectRequest(table, "DISTINCT group_id")

  val selectArtifactName: Query[Project.Reference, Artifact.Name] =
    selectRequest(table, Seq("DISTINCT artifact_name"), projectReferenceFields)

  val selectPlatformByArtifactName: Query[(Project.Reference, Artifact.Name), Platform] =
    selectRequest(table, Seq("DISTINCT platform"), projectReferenceFields :+ "artifact_name")

  val selectMavenReference: Query0[Artifact.MavenReference] =
    selectRequest(table, """DISTINCT group_id, artifact_id, "version"""")

  val selectMavenReferenceWithNoReleaseDate: Query0[Artifact.MavenReference] =
    selectRequest(table, """group_id, artifact_id, "version"""", where = Some("release_date is NULL"))

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

  val findLastSemanticVersionNotPrerelease: Query[Project.Reference, SemanticVersion] =
    selectRequest1(
      table,
      "version",
      where = Some("organization=? AND repository=? AND is_semantic='true' and is_prerelease='false'"),
      orderBy = Some("release_date DESC"),
      limit = Some(1L)
    )

  val findLastVersion: Query[Project.Reference, SemanticVersion] =
    selectRequest1(
      table,
      "version",
      where = Some("organization=? AND repository=?"),
      orderBy = Some("release_date DESC"),
      limit = Some(1L)
    )
}
