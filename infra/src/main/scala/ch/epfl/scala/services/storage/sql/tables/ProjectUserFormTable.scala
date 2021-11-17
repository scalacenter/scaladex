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
    "primaryTopic"
  )

  val table: Fragment = Fragment.const0("project_user_data")
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: NewProject, userData: NewProject.DataForm): Fragment =
    fr0"${p.organization}, ${p.repository}, ${userData.defaultStableVersion}, ${userData.defaultArtifact}," ++
      fr0" ${userData.strictVersions}, ${userData.customScalaDoc}, ${userData.documentationLinks}, ${userData.deprecated}, ${userData.contributorsWanted}," ++
      fr0" ${userData.artifactDeprecations}, ${userData.cliArtifacts}, ${userData.primaryTopic}"

  def insert(p: NewProject)(userData: NewProject.DataForm): doobie.Update0 =
    buildInsert(table, fieldsFr, values(p, userData)).update

  def insertOrUpdate(p: NewProject)(
      userDataForm: NewProject.DataForm
  ): doobie.Update0 = {
    val onConflict = fr0"organization, repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      table,
      fieldsFr,
      values(p, userDataForm),
      onConflict,
      doAction
    ).update
  }

  def update(p: NewProject)(
      userData: NewProject.DataForm
  ): doobie.Update0 = {
    val fields = fr0"defaultStableVersion=${userData.defaultStableVersion}, defaultArtifact=${userData.defaultArtifact}," ++
      fr0" strictVersions=${userData.strictVersions}, customScalaDoc=${userData.customScalaDoc}, documentationLinks=${userData.documentationLinks}," ++
      fr0" deprecated=${userData.deprecated}, contributorsWanted=${userData.contributorsWanted}," ++
      fr0" artifactDeprecations=${userData.artifactDeprecations}, cliArtifacts=${userData.cliArtifacts}, primaryTopic=${userData.primaryTopic}"
    buildUpdate(table, fields, whereRef(p.reference)).update
  }

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(table, fr0"count(*)").query[Long]

  def indexedProjectUserForm(): doobie.Query0[Long] =
    buildSelect(table, fr0"count(*)").query[Long]

  val formDataReader: Read[NewProject.DataForm] =
    Read[(Organization, Repository, NewProject.DataForm)].map { case (_, _, userFormData) => userFormData }
}
