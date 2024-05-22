package scaladex.infra.sql

import doobie._
import scaladex.core.model.GithubInfo
import scaladex.core.model.Project
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

object GithubInfoTable {
  val table: String = "github_info"
  val referenceFields: Seq[String] = Seq("organization", "repository")
  val infoFields: Seq[String] = Seq(
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
    "open_issues",
    "scala_percentage",
    "license",
    "commit_activity"
  )

  val insert: Update[(Project.Reference, GithubInfo)] =
    insertRequest(table, referenceFields ++ infoFields)

  val insertOrUpdate: Update[(Project.Reference, GithubInfo, GithubInfo)] =
    insertOrUpdateRequest(table, referenceFields ++ infoFields, referenceFields, infoFields)

  val count: Query0[Long] = selectRequest(table, Seq("COUNT(*)"))
}
