package ch.epfl.scala.services.storage.sql.tables

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
    "released_at",
    "resolver",
    "licenses",
    "isNonStandardLib"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(r: NewRelease): Fragment =
    fr0"${r.maven.groupId}, ${r.maven.artifactId}, ${r.version}, ${r.organization}, ${r.repository}," ++
      fr0" ${r.artifactName}, ${r.platform}, ${r.description}, ${r.releasedAt}, ${r.resolver}, ${r.licenses}, ${r.isNonStandardLib}"

  def insert(elt: NewRelease): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: Seq[NewRelease]): doobie.ConnectionIO[Int] =
    Update[NewRelease](insert(elts.head).sql).updateMany(elts)

  def indexedReleased(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectReleases(ref: NewProject.Reference): doobie.Query0[NewRelease] =
    buildSelect(tableFr, fr0"*", where(ref))
      .query[NewRelease]

  def selectReleases(
      ref: NewProject.Reference,
      artifactName: ArtifactName
  ): doobie.Query0[NewRelease] =
    buildSelect(
      tableFr,
      fr0"*",
      where(ref) ++ fr0" AND artifact=$artifactName"
    ).query[NewRelease]

  def selectPlatform(): doobie.Query0[
    (NewProject.Organization, NewProject.Repository, Platform)
  ] =
    buildSelect(
      tableFr,
      fr0"organization, repository, platform",
      fr0"GROUP BY organization, repository, platform"
    ).query[(NewProject.Organization, NewProject.Repository, Platform)]

  def selectOldestRelease(ref: NewProject.Reference): doobie.Query0[NewRelease] =
    buildSelect(
      tableFr,
      fr0"*",
      where(ref) ++ fr0" AND released_at IS NOT NULL" ++
        fr0" ORDER BY released_at" ++
        fr0" LIMIT 1"
    ).query[NewRelease]

}
