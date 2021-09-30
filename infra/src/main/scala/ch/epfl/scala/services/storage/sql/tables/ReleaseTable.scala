package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object ReleaseTable {
  private val _ = documentationLinksMeta
  private[sql] val table = "releases"
  private[sql] val fields = Seq(
    "groupId",
    "artifactId",
    "version",
    "organization",
    "repository",
    "artifact",
    "platform",
    "description",
    "released",
    "resolver",
    "licenses",
    "isNonStandardLib"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(r: NewRelease): Fragment =
    fr0"${r.maven.groupId}, ${r.maven.artifactId}, ${r.version}, ${r.organization}, ${r.repository}," ++
      fr0" ${r.artifactName}, ${r.platform}, ${r.description}, ${r.released}, ${r.resolver}, ${r.licenses}, ${r.isNonStandardLib}"

  def insert(elt: NewRelease): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: Seq[NewRelease]): doobie.ConnectionIO[Int] =
    Update[NewRelease](insert(elts.head).sql).updateMany(elts)

  def indexedReleased(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectReleases(ref: Project.Reference): doobie.Query0[NewRelease] =
    buildSelect(tableFr, fr0"*", where(ref.org, ref.repo)).query[NewRelease]

  def selectReleases(
      ref: Project.Reference,
      artifactName: ArtifactName
  ): doobie.Query0[NewRelease] =
    buildSelect(
      tableFr,
      fr0"*",
      fr0"WHERE organization=${ref.org} AND repository=${ref.repo} AND artifact=$artifactName"
    ).query[NewRelease]

  def selectPlatform(): doobie.Query0[
    (NewProject.Organization, NewProject.Repository, Platform)
  ] =
    buildSelect(
      tableFr,
      fr0"organization, repository, platform",
      fr0"GROUP BY organization, repository, platform"
    ).query[(NewProject.Organization, NewProject.Repository, Platform)]
}
