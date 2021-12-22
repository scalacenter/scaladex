package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import doobie.implicits._
import doobie.util.fragment.Fragment

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
    "readme",
    "contributors",
    "commits",
    "topics",
    "contributingGuide",
    "codeOfConduct",
    "chatroom",
    "beginnerIssues"
  )

  val table: String = "github_info"
  val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(g: GithubInfo): Fragment =
    fr0"${g.organization}, ${g.repository}, ${g.homepage}, ${g.description}, ${g.logo}, ${g.stars}, ${g.forks}," ++
      fr0" ${g.watchers}, ${g.issues}, ${g.readme}, ${g.contributors}," ++
      fr0" ${g.commits}, ${g.topics}, ${g.contributingGuide}, ${g.codeOfConduct}, ${g.chatroom}, ${g.beginnerIssues}"

  def insert(elt: GithubInfo): doobie.Update0 =
    buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertOrUpdate(g: GithubInfo): doobie.Update0 = {
    val onConflictFields = fr0"organization, repository"
    val fields =
      fr0"homepage=${g.homepage}, description=${g.description}, logo=${g.logo}," ++
        fr0" stars=${g.stars}, forks=${g.forks}, watchers=${g.watchers}, issues=${g.issues}, readme=${g.readme}, contributors=${g.contributors}," ++
        fr0" commits=${g.commits}, topics=${g.topics}, contributingGuide=${g.contributingGuide}," ++
        fr0" codeOfConduct=${g.codeOfConduct}, chatroom=${g.chatroom}, beginnerIssues=${g.beginnerIssues}"
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
