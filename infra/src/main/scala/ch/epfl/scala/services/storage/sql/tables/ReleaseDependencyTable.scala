package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildInsert
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildSelect
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object ReleaseDependencyTable {
  private val _ =
    dependencyWriter // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "release_dependencies"
  private[sql] val fields = Seq(
    "source_groupId",
    "source_artifactId",
    "source_version",
    "target_groupId",
    "target_artifactId",
    "target_version",
    "scope"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val tableWithSourceReleases = Fragment.const0(
    s"$table d " +
      s"INNER JOIN ${ReleaseTable.table} r ON " +
      s"d.source_groupid = r.groupid AND " +
      s"d.source_artifactid = r.artifactid AND " +
      s"d.source_version = r.version"
  )

  private val tableWithTargetReleases = Fragment.const0(
    s"$table d " +
      s"LEFT JOIN ${ReleaseTable.table} r ON " +
      s"d.target_groupid = r.groupid AND " +
      s"d.target_artifactid = r.artifactid AND " +
      s"d.target_version = r.version"
  )

  private val fullJoin =
    fr0"( " ++ tableWithSourceReleases ++ fr0") d " ++
      Fragment.const0(
        s"INNER JOIN ${ReleaseTable.table} t ON " +
          s"d.target_groupid = t.groupid AND " +
          s"d.target_artifactid = t.artifactid AND " +
          s"d.target_version = t.version"
      )

  private val fieldstableWithRelease = Fragment.const0(
    (
      fields.map("d." + _) ++
        ReleaseTable.fields.map("r." + _)
    ).mkString(", ")
  )

  private def values(d: ReleaseDependency): Fragment =
    fr0"${d.source.groupId}, ${d.source.artifactId} ,${d.source.version}, ${d.target.groupId}," ++
      fr0" ${d.target.artifactId}, ${d.target.version}, ${d.scope}"

  def insert(elt: ReleaseDependency): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: Seq[ReleaseDependency]): doobie.ConnectionIO[Int] =
    Update[ReleaseDependency](insert(elts.head).sql).updateMany(elts)

  def indexedDependencies(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def find(m: MavenReference): doobie.Query0[ReleaseDependency] =
    buildSelect(
      tableFr,
      fieldsFr,
      fr0"WHERE source_groupId=${m.groupId} AND source_artifactId=${m.artifactId} AND source_version=${m.version}"
    ).query[ReleaseDependency]

  def selectDirectDependencies(
      release: NewRelease
  ): doobie.Query0[ReleaseDependency.Direct] = {
    buildSelect(
      tableWithTargetReleases,
      fieldstableWithRelease,
      fr0"WHERE d.source_groupId=${release.maven.groupId}" ++
        fr0" AND d.source_artifactId=${release.maven.artifactId}" ++
        fr0" AND d.source_version=${release.maven.version}"
    )
      .query[ReleaseDependency.Direct]
  }

  def selectReverseDependencies(
      release: NewRelease
  ): doobie.Query0[ReleaseDependency.Reverse] =
    buildSelect(
      tableWithSourceReleases,
      fieldstableWithRelease,
      fr0"WHERE d.target_groupId=${release.maven.groupId} AND" ++
        fr0" d.target_artifactId=${release.maven.artifactId}" ++
        fr0" AND d.target_version=${release.maven.version}"
    )
      .query[ReleaseDependency.Reverse]

  def getAllProjectDependencies(): doobie.Query0[ProjectDependency] =
    buildSelect(
      fullJoin,
      fr0"DISTINCT d.organization, d.repository, t.organization, t.repository",
      fr0"GROUP BY d.organization, d.repository, t.organization, t.repository"
    ).query[ProjectDependency]
}
