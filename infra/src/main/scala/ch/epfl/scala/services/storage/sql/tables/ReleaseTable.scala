package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object ReleaseTable {
  private val _ = documentationLinksMeta
  private val table = "releases"
  private val fields = Seq(
    "groupId",
    "artifactId",
    "version",
    "organization",
    "repository",
    "artifact",
    "target",
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
      fr0" ${r.artifactName}, ${r.target}, ${r.description}, ${r.released}, ${r.resolver}, ${r.licenses}, ${r.isNonStandardLib}"

  def insert(elt: NewRelease): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: Seq[NewRelease]): doobie.ConnectionIO[Int] =
    Update[NewRelease](insert(elts.head).sql).updateMany(elts)

  def indexedReleased(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectReleases(ref: Project.Reference): doobie.Query0[NewRelease] =
    buildSelect(tableFr, fr0"*", where(ref.org, ref.repo)).query[NewRelease]

}
