package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildInsert
import ch.epfl.scala.utils.DoobieUtils.Fragments.buildSelect
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object DependenciesTable {
  private val _ =
    dependencyWriter // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "dependencies"
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
  private val fieldstableWithRelease = Fragment.const0(
    (
      fields.map("d." + _) ++
        ReleaseTable.fields.map("r." + _)
    ).mkString(", ")
  )

  private def values(d: NewDependency): Fragment =
    fr0"${d.source.groupId}, ${d.source.artifactId} ,${d.source.version}, ${d.target.groupId}," ++
      fr0" ${d.target.artifactId}, ${d.target.version}, ${d.scope}"

  def insert(elt: NewDependency): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: Seq[NewDependency]): doobie.ConnectionIO[Int] =
    Update[NewDependency](insert(elts.head).sql).updateMany(elts)

  def indexedDependencies(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def find(m: MavenReference): doobie.Query0[NewDependency] =
    buildSelect(
      tableFr,
      fieldsFr,
      fr0"WHERE source_groupId=${m.groupId} AND source_artifactId=${m.artifactId} AND source_version=${m.version}"
    ).query[NewDependency]

  def selectDirectDependencies(
      release: NewRelease
  ): doobie.Query0[NewDependency.Direct] = {
    buildSelect(
      tableWithTargetReleases,
      fieldstableWithRelease,
      fr0"WHERE d.source_groupId=${release.maven.groupId} AND d.source_artifactId=${release.maven.artifactId} AND d.source_version=${release.maven.version}"
    )
      .query[NewDependency.Direct]
  }

  def selectReverseDependencies(
      release: NewRelease
  ): doobie.Query0[NewDependency.Reverse] =
    buildSelect(
      tableWithSourceReleases,
      fieldstableWithRelease,
      fr0"WHERE d.target_groupId=${release.maven.groupId} AND d.target_artifactId=${release.maven.artifactId} AND d.target_version=${release.maven.version}"
    )
      .query[NewDependency.Reverse]
}
