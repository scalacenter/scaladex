package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.newModel.NewProject
import doobie.util.fragment.Fragment
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import cats.data.NonEmptyList
import doobie.util.update.Update
import doobie.implicits._
import ch.epfl.scala.utils.DoobieUtils.Mappings._

object ProjectTable {
  private val _ = documentationLinksMeta
  private val table = "projects"
  private val fields = Seq(
    "organization",
    "repository",
    "defaultStableVersion",
    "defaultArtifact",
    "strictVersions",
    "customScalaDoc",
    "documentationLinks",
    "deprecated",
    "contributorsWanted",
    "artifactDeprecations",
    "cliArtifacts",
    "primaryTopic",
    "esId"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: NewProject): Fragment =
    fr0"${p.organization}, ${p.repository}, ${p.defaultStableVersion}, ${p.defaultArtifact}, ${p.strictVersions}, ${p.customScalaDoc}, ${p.documentationLinks}, ${p.deprecated}, ${p.contributorsWanted}, ${p.artifactDeprecations}, ${p.cliArtifacts}, ${p.primaryTopic}, ${p.esId}"

  def insert(elt: NewProject): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

}
