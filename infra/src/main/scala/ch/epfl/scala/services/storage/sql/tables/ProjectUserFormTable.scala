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
  private def values(p: NewProject, userData: NewProject.FormData): Fragment =
    fr0"${p.organization}, ${p.repository}, ${userData.defaultStableVersion}, ${userData.defaultArtifact}," ++
      fr0" ${userData.strictVersions}, ${userData.customScalaDoc}, ${userData.documentationLinks}, ${userData.deprecated}, ${userData.contributorsWanted}," ++
      fr0" ${userData.artifactDeprecations}, ${userData.cliArtifacts}, ${userData.primaryTopic}"

  def insert(elt: NewProject)(
      userserData: NewProject.FormData
  ): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt, userserData)).update

  def indexedProjects(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  def selectOne(
      org: Organization,
      repo: Repository
  ): doobie.ConnectionIO[Option[NewProject.FormData]] =
    selectOneQuery(org, repo).option

  def indexedProjectUserForm(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  private[tables] def selectOneQuery(
      org: Organization,
      repo: Repository
  ): doobie.Query0[NewProject.FormData] =
    buildSelect(
      tableFr,
      fieldsFr,
      fr0"WHERE organization=$org AND repository=$repo"
    ).query[NewProject.FormData](formDataReader)

  val formDataReader: Read[NewProject.FormData] =
    Read[(Organization, Repository, NewProject.FormData)].map {
      case (_, _, userFormData) => userFormData
    }
}
