package scaladex.infra.storage.sql.tables

import doobie._
import scaladex.core.model.GithubInfo
import scaladex.core.model.Project
import scaladex.infra.util.DoobieUtils.Mappings._
import scaladex.infra.util.DoobieUtils._

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
    "chatroom",
    "open_issues"
  )

  val insert: Update[(Project.Reference, GithubInfo)] =
    insertRequest(table, referenceFields ++ infoFields)

  val insertOrUpdate: Update[(Project.Reference, GithubInfo, GithubInfo)] =
    insertOrUpdateRequest(table, referenceFields ++ infoFields, referenceFields, infoFields)

  val count: Query0[Long] =
    selectRequest(table, "COUNT(*)")
}
