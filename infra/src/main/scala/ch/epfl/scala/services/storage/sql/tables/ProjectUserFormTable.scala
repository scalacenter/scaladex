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
  private val table = "project_user_data"
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
    "primaryTopic"
  )

  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: NewProject, userData: NewProject.DataForm): Fragment =
    fr0"${p.organization}, ${p.repository}, ${userData.defaultStableVersion}, ${userData.defaultArtifact}," ++
      fr0" ${userData.strictVersions}, ${userData.customScalaDoc}, ${userData.documentationLinks}, ${userData.deprecated}, ${userData.contributorsWanted}," ++
      fr0" ${userData.artifactDeprecations}, ${userData.cliArtifacts}, ${userData.primaryTopic}"

  def insert(p: NewProject)(userData: NewProject.DataForm): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(p, userData)).update

  def insertOrUpdate(p: NewProject)(
      userDataForm: NewProject.DataForm
  ): doobie.Update0 = {
    val onConflict = fr0"organization, repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      tableFr,
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
    buildUpdate(tableFr, fields, where(p.reference)).update
  }

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectOne(ref: NewProject.Reference): doobie.ConnectionIO[Option[NewProject.DataForm]] =
    selectOneQuery(ref).option

  def indexedProjectUserForm(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  private[tables] def selectOneQuery(ref: NewProject.Reference): doobie.Query0[NewProject.DataForm] =
    buildSelect(
      tableFr,
      fieldsFr,
      where(ref)
    ).query[NewProject.DataForm](formDataReader)

  val formDataReader: Read[NewProject.DataForm] =
    Read[(Organization, Repository, NewProject.DataForm)].map { case (_, _, userFormData) => userFormData }
}
