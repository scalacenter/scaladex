package ch.epfl.scala.services.storage.sql.tables

import java.time.Instant

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
  val fields: Seq[String] = Seq(
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
    "updated_at"
  )

  val table: Fragment = Fragment.const0("github_info")
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(p: NewProject.Reference, g: GithubInfo): Fragment =
    fr0"${p.organization}, ${p.repository}, ${g.name}, ${g.owner}, " ++
      fr0"${g.homepage}, ${g.description}, ${g.logo}, ${g.stars}, ${g.forks}," ++
      fr0" ${g.watchers}, ${g.issues},${g.readme}, ${g.contributors}, ${g.contributorCount}," ++
      fr0" ${g.commits}, ${g.topics}, ${g.contributingGuide}, ${g.codeOfConduct}, ${g.chatroom}," ++
      fr0" ${g.beginnerIssuesLabel}, ${g.beginnerIssues}, ${g.selectedBeginnerIssues}, ${g.updatedAt}"

  def insert(p: NewProject.Reference)(elt: GithubInfo): doobie.Update0 =
    buildInsert(table, fieldsFr, values(p, elt)).update

  def insertOrUpdate(p: NewProject.Reference)(g: GithubInfo, now: Instant): doobie.Update0 = {
    val onConflictFields = fr0"organization, repository"
    val fields =
      fr0"name=${g.name}, owner=${g.owner}, homepage=${g.homepage}, description=${g.description}, logo=${g.logo}," ++
        fr0" stars=${g.stars}, forks=${g.forks}, watchers=${g.watchers}, issues=${g.issues}, readme=${g.readme}, contributors=${g.contributors}," ++
        fr0" contributorCount=${g.contributorCount}, commits=${g.commits}, topics=${g.topics}, contributingGuide=${g.contributingGuide}," ++
        fr0" codeOfConduct=${g.codeOfConduct}, chatroom=${g.chatroom}, updated_at=$now"
    val updateAction = fr"UPDATE SET" ++ fields
    buildInsertOrUpdate(
      table,
      fieldsFr,
      values(p, g),
      onConflictFields,
      updateAction
    ).update
  }

  def selectAllTopics(): doobie.Query0[Set[String]] =
    buildSelect(table, fr0"topics", fr0"where topics != ''")
      .query[Set[String]]

  val githubInfoReader: Read[GithubInfo] =
    Read[(Organization, Repository, GithubInfo)].map { case (_, _, githubInfo) => githubInfo }

  def indexedGithubInfo(): doobie.Query0[Long] =
    buildSelect(table, fr0"count(*)").query[Long]
}
