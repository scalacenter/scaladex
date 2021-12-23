package scaladex.infra.storage.sql.tables

import doobie.implicits._
import doobie.util.fragment.Fragment
import scaladex.core.model.GithubInfo
import scaladex.infra.util.DoobieUtils.Fragments._
import scaladex.infra.util.DoobieUtils.Mappings._

object GithubInfoTable {
  private val _ =
    contributorMeta // for intellij not remove DoobieUtils.Mappings import
  val fields: Seq[String] = Seq(
    "organization",
    "repository",
    "homepage",
    "description",
    "logo",
    "stars",
    "forks",
    "watchers",
    "issues",
    "creation_date",
    "readme",
    "contributors",
    "commits",
    "topics",
    "contributing_guide",
    "code_of_conduct",
    "chatroom",
    "open_issues"
  )

  val table: String = "github_info"
  val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(g: GithubInfo): Fragment =
    fr0"${g.organization}, ${g.repository}, ${g.homepage}, ${g.description}, ${g.logo}, ${g.stars}, ${g.forks}," ++
      fr0" ${g.watchers}, ${g.issues}, ${g.creationDate}, ${g.readme}, ${g.contributors}," ++
      fr0" ${g.commits}, ${g.topics}, ${g.contributingGuide}, ${g.codeOfConduct}, ${g.chatroom}, ${g.beginnerIssues}"

  def insert(elt: GithubInfo): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertOrUpdate(g: GithubInfo): doobie.Update0 = {
    val onConflictFields = fr0"organization, repository"
    val fields =
      fr0"homepage=${g.homepage}, description=${g.description}, logo=${g.logo}," ++
        fr0" stars=${g.stars}, forks=${g.forks}, watchers=${g.watchers}, issues=${g.issues}, creation_date=${g.creationDate}," ++
        fr0" readme=${g.readme}, contributors=${g.contributors}," ++
        fr0" commits=${g.commits}, topics=${g.topics}, contributing_guide=${g.contributingGuide}," ++
        fr0" code_of_conduct=${g.codeOfConduct}, chatroom=${g.chatroom}, open_issues=${g.beginnerIssues}"
    val updateAction = fr"UPDATE SET" ++ fields
    buildInsertOrUpdate(
      tableFr,
      fieldsFr,
      values(g),
      onConflictFields,
      updateAction
    ).update
  }

  def selectAllTopics(): doobie.Query0[Set[String]] =
    buildSelect(tableFr, fr0"topics", fr0"where topics != ''").query[Set[String]]

  def indexedGithubInfo(): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]
}
