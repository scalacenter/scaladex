package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment

object GithubInfoTable {
  private val _ =
    contributorMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "github_info"
  private val fields = Seq(
    "organization",
    "repository",
    "name",
    "owner",
    "homepage",
    "description",
    "logo",
    "stars",
    "forks",
    "watchers",
    "issues",
    "readme",
    "contributors",
    "contributorCount",
    "commits",
    "topics",
    "contributingGuide",
    "codeOfConduct",
    "chatroom",
    "beginnerIssuesLabel",
    "beginnerIssues",
    "selectedBeginnerIssues",
    "filteredBeginnerIssues"
  )

  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(p: NewProject, g: GithubInfo): Fragment =
    fr0"${p.organization}, ${p.repository}, ${g.name}, ${g.owner}, " ++
      fr0"${g.homepage}, ${g.description}, ${g.logo}, ${g.stars}, ${g.forks}," ++
      fr0" ${g.watchers}, ${g.issues},${g.readme}, ${g.contributors}, ${g.contributorCount}," ++
      fr0" ${g.commits}, ${g.topics}, ${g.contributingGuide}, ${g.codeOfConduct}, ${g.chatroom}," ++
      fr0" ${g.beginnerIssuesLabel}, ${g.beginnerIssues}, ${g.selectedBeginnerIssues}, ${g.filteredBeginnerIssues}"

  def insert(p: NewProject)(elt: GithubInfo): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(p, elt)).update

  def selectOne(
      org: Organization,
      repo: Repository
  ): doobie.ConnectionIO[Option[GithubInfo]] =
    selectOneQuery(org, repo).option

  private[tables] def selectOneQuery(
      org: Organization,
      repo: Repository
  ): doobie.Query0[GithubInfo] =
    buildSelect(
      tableFr,
      fieldsFr,
      fr0"WHERE organization=$org AND repository=$repo"
    ).query[GithubInfo](githubInfoReader)

  val githubInfoReader: Read[GithubInfo] =
    Read[(Organization, Repository, GithubInfo)].map {
      case (_, _, githubInfo) => githubInfo
    }

  def indexedGithubInfo(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

}
