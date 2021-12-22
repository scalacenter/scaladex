package scaladex.infra.storage.sql.tables

import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment
import scaladex.core.model.Project
import scaladex.core.model.Project._
import scaladex.infra.util.DoobieUtils.Fragments._
import scaladex.infra.util.DoobieUtils.Mappings._

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

  val table: String = "project_user_data"
  val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private def values(p: Project.Reference, userData: Project.DataForm): Fragment =
    fr0"${p.organization}, ${p.repository}, ${userData.defaultStableVersion}, ${userData.defaultArtifact}," ++
      fr0" ${userData.strictVersions}, ${userData.customScalaDoc}, ${userData.documentationLinks}, ${userData.deprecated}, ${userData.contributorsWanted}," ++
      fr0" ${userData.artifactDeprecations}, ${userData.cliArtifacts}, ${userData.primaryTopic}, ${userData.beginnerIssuesLabel}"

  def insertOrUpdate(ref: Project.Reference)(
      userDataForm: Project.DataForm
  ): doobie.Update0 = {
    val onConflict = fr0"organization, repository"
    val doAction = fr0"NOTHING"
    buildInsertOrUpdate(
      tableFr,
      fieldsFr,
      values(ref, userDataForm),
      onConflict,
      doAction
    ).update
  }

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def indexedProjectUserForm(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  val formDataReader: Read[Project.DataForm] =
    Read[(Organization, Repository, Project.DataForm)].map { case (_, _, userFormData) => userFormData }
}
