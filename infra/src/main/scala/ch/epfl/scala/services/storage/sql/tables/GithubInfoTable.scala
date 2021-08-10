package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.services.storage.sql.tables.ProjectTable.tableFr
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import doobie.implicits._
import doobie.util.fragment.Fragment
import ch.epfl.scala.utils.DoobieUtils.Mappings._

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
    fr0"${p.organization}, ${p.repository}, ${g.name}, ${g.owner}, ${g.homepage}, ${g.description}, ${g.logo}, ${g.stars}, ${g.forks}, ${g.watchers}, ${g.issues},${g.readme}, ${g.contributors}, ${g.contributorCount}, ${g.commits}, ${g.topics}, ${g.contributingGuide}, ${g.codeOfConduct}, ${g.chatroom}, ${g.beginnerIssuesLabel}, ${g.beginnerIssues}, ${g.selectedBeginnerIssues}, ${g.filteredBeginnerIssues}"

  def insert(p: NewProject)(elt: GithubInfo): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(p, elt)).update

  def indexedGithubInfo(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]
}
