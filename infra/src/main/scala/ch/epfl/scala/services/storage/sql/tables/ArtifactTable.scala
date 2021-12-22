package ch.epfl.scala.services.storage.sql.tables

import java.time.Instant

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.Artifact.Name
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import ch.epfl.scala.utils.DoobieUtils._
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object ArtifactTable {
  private val _ = documentationLinksMeta
  private[sql] val table = "artifacts"

  private[sql] val fields = Seq(
    "group_id",
    "artifact_id",
    "version",
    "artifact_name",
    "platform",
    "organization",
    "repository",
    "description",
    "release_date",
    "resolver",
    "licenses",
    "isNonStandardLib"
  )
  private val tableFr: Fragment = Fragment.const0(table)

  val insert: Update[Artifact] = insertRequest(table, fields)

  def indexedArtifacts(): Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  val updateProjectRef: Update[(Project.Reference, Artifact.MavenReference)] =
    Update[(Project.Reference, Artifact.MavenReference)](
      s"UPDATE $table SET organization=?, repository=? WHERE group_id=? AND artifact_id=? AND version=?"
    )

  def selectArtifacts(ref: Project.Reference): Query0[Artifact] =
    buildSelect(tableFr, fr0"*", whereRef(ref)).query[Artifact]

  def selectArtifacts(ref: Project.Reference, artifactName: Name): doobie.Query0[Artifact] =
    buildSelect(
      tableFr,
      fr0"*",
      whereRef(ref) ++ fr0" AND artifact_name=$artifactName"
    ).query[Artifact]

  def selectPlatform(): Query0[(Project.Organization, Project.Repository, Platform)] =
    buildSelect(
      tableFr,
      fr0"organization, repository, platform",
      fr0"GROUP BY organization, repository, platform"
    ).query[(Project.Organization, Project.Repository, Platform)]

  def findOldestArtifactsPerProjectReference(): Query0[(Instant, Project.Reference)] =
    buildSelect(
      tableFr,
      fr0"min(release_date) as oldest_artifact, organization, repository",
      fr0"where release_date IS NOT NULL" ++ space ++ fr0"group by organization, repository"
    ).query[(Instant, Project.Reference)]

}
