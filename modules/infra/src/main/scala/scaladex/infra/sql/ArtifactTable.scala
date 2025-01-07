package scaladex.infra.sql

import java.time.Instant

import doobie.*
import doobie.util.update.Update
import scaladex.core.model.Artifact.*
import scaladex.core.model.*
import scaladex.infra.sql.DoobieMappings.*
import scaladex.infra.sql.DoobieUtils.*

object ArtifactTable:
  private[sql] val table = "artifacts"

  val referenceFields: Seq[String] = Seq("group_id", "artifact_id", "version")
  val projectReferenceFields: Seq[String] = Seq("organization", "repository")

  // Don't use `select *...` but `select fields`
  // the table have more fields than in Artifact instance
  private[sql] val mainFields = referenceFields ++
    projectReferenceFields ++
    Seq(
      "description",
      "release_date",
      "resolver",
      "licenses",
      "is_non_standard_Lib",
      "full_scala_version",
      "scaladoc_url",
      "version_scheme",
      "developers"
    )
  // these field are usually excluded when we read artifacts from the artifacts table.
  val extraFields: Seq[String] = Seq("artifact_name", "platform", "language_version", "is_prerelease")
  val isLatestVersion: String = "is_latest_version"

  val insertIfNotExist: Update[Artifact] =
    type T = (Artifact, Name, Platform, Language, Boolean, Artifact)
    insertOrUpdateRequest[T](table, mainFields ++ extraFields, referenceFields, mainFields)
      .contramap[Artifact](a => (a, a.name, a.platform, a.language, a.version.isPreRelease, a))

  val count: Query0[Long] =
    selectRequest(table, Seq("COUNT(*)"))

  val countVersionsByProject: Query[Project.Reference, Long] =
    selectRequest(table, Seq("Count(DISTINCT version)"), projectReferenceFields)

  val updateProjectRef: Update[(Project.Reference, Reference)] =
    updateRequest(table, projectReferenceFields, referenceFields)

  val updateReleaseDate: Update[(Instant, Reference)] =
    updateRequest(table, Seq("release_date"), referenceFields)

  def selectAllArtifacts(language: Option[Language], platform: Option[Platform]): Query0[Artifact] =
    val where = language.map(v => s"language_version='${v.value}'").toSeq ++ platform.map(p => s"platform='${p.value}'")
    selectRequest(table, mainFields, where = where)

  val selectArtifactByGroupIdAndArtifactId: Query[(GroupId, ArtifactId), Artifact] =
    selectRequest(table, mainFields, Seq("group_id", "artifact_id"))

  def selectVersionByGroupIdAndArtifactId(stableOnly: Boolean): Query[(GroupId, ArtifactId), Version] =
    selectRequest1(
      table,
      Seq("version"),
      keys = Seq("group_id", "artifact_id"),
      where = stableOnlyFilter(stableOnly).toSeq
    )

  val selectLatestArtifact: Query[(GroupId, ArtifactId), Artifact] =
    selectRequest1(table, mainFields, keys = Seq("group_id", "artifact_id"), where = Seq("is_latest_version"))

  val selectArtifactByProject: Query[Project.Reference, Artifact] =
    selectRequest1(
      table,
      mainFields,
      keys = projectReferenceFields,
      orderBy = Some("release_date DESC"),
      limit = Some(10000)
    )

  def selectArtifactRefByProject(stableOnly: Boolean): Query[Project.Reference, Reference] =
    // implicit val artifactNamesMeta = Meta[Array[String]].timap(_.toSeq.map(Name.apply))(_.map(_.value).toArray)
    // val nameFilter = if (names.isEmpty) "?::varchar[] IS NOT NULL" else "artifact_name = ANY(?)"
    selectRequest1[Project.Reference, Reference](
      table,
      referenceFields,
      keys = projectReferenceFields,
      where = stableOnlyFilter(stableOnly)
    )

  def selectArtifactRefByProjectAndName: Query[(Project.Reference, Artifact.Name), Reference] =
    selectRequest1(
      table,
      referenceFields,
      keys = projectReferenceFields ++ Seq("artifact_name")
    )

  def selectArtifactRefByProjectAndVersion: Query[(Project.Reference, Version), Reference] =
    selectRequest1(
      table,
      referenceFields,
      keys = projectReferenceFields ++ Seq("version")
    )

  def selectArtifactByProjectAndName(stableOnly: Boolean): Query[(Project.Reference, Name), Artifact] =
    selectRequest1(
      table,
      mainFields,
      keys = Seq("organization", "repository", "artifact_name"),
      where = stableOnlyFilter(stableOnly).toSeq
    )

  val selectArtifactByProjectAndNameAndVersion: Query[(Project.Reference, Name, Version), Artifact] =
    selectRequest1(
      table,
      mainFields,
      keys = Seq("organization", "repository", "artifact_name", "version")
    )

  private def stableOnlyFilter(stableOnly: Boolean): Seq[String] =
    if stableOnly then Seq("is_prerelease=false") else Seq.empty

  val selectByReference: Query[Reference, Artifact] =
    selectRequest(table, mainFields, referenceFields)

  val selectGroupIds: Query0[GroupId] =
    selectRequest(table, Seq("DISTINCT group_id"))

  val selectArtifactIds: Query[Project.Reference, (GroupId, ArtifactId)] =
    selectRequest(table, Seq("DISTINCT group_id", "artifact_id"), keys = projectReferenceFields)

  val selectReferences: Query0[Reference] =
    selectRequest(table, Seq("DISTINCT group_id", "artifact_id", "\"version\""))

  val selectReferencesByProject: Query[Project.Reference, Reference] =
    selectRequest(table, Seq("DISTINCT group_id", "artifact_id", "\"version\""), keys = projectReferenceFields)

  val selectMavenReferenceWithNoReleaseDate: Query0[Reference] =
    selectRequest(table, Seq("group_id", "artifact_id", "\"version\""), where = Seq("release_date is NULL"))

  val selectOldestByProject: Query0[(Instant, Project.Reference)] =
    selectRequest(
      table,
      Seq("MIN(release_date)", "organization", "repository"),
      where = Seq("release_date IS NOT NULL"),
      groupBy = projectReferenceFields
    )

  def selectLatestArtifacts: Query[Project.Reference, Artifact] =
    selectRequest1(table, mainFields, where = Seq("organization=?", "repository=?", "is_latest_version=true"))

  def setLatestVersion: Update[Reference] =
    updateRequest0(table, set = Seq("is_latest_version=true"), where = Seq("group_id=?", "artifact_id=?", "version=?"))

  def unsetOthersLatestVersion: Update[Reference] =
    updateRequest0(
      table,
      set = Seq("is_latest_version=false"),
      where = Seq("group_id=?", "artifact_id=?", "version<>?")
    )
end ArtifactTable
