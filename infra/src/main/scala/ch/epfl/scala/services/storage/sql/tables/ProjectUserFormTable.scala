package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment

object ProjectUserFormTable {
  private val _ = documentationLinksMeta
  val fields: Seq[String] = Seq(
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
    "beginnerIssuesLabel"
  )

  val table: Fragment = Fragment.const0("project_user_data")
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: NewProject.Reference, userData: NewProject.DataForm): Fragment =
    fr0"${p.organization}, ${p.repository}, ${userData.defaultStableVersion}, ${userData.defaultArtifact}," ++
      fr0" ${userData.strictVersions}, ${userData.customScalaDoc}, ${userData.documentationLinks}, ${userData.deprecated}, ${userData.contributorsWanted}," ++
      fr0" ${userData.artifactDeprecations}, ${userData.cliArtifacts}, ${userData.primaryTopic}, ${userData.beginnerIssuesLabel}"

  def insertOrUpdate(ref: NewProject.Reference)(
      userDataForm: NewProject.DataForm
  ): doobie.Update0 = {
    val onConflict = fr0"organization, repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      table,
      fieldsFr,
      values(ref, userDataForm),
      onConflict,
      doAction
    ).update
  }

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(table, fr0"count(*)").query[Long]

  def indexedProjectUserForm(): doobie.Query0[Long] =
    buildSelect(table, fr0"count(*)").query[Long]

  val formDataReader: Read[NewProject.DataForm] =
    Read[(Organization, Repository, NewProject.DataForm)].map { case (_, _, userFormData) => userFormData }
}
