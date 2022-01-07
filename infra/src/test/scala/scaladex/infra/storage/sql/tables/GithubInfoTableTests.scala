package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite
import scaladex.infra.storage.sql.tables.GithubInfoTable

class GithubInfoTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insert")(check(GithubInfoTable.insert))
  it("check insertOrUpdate")(check(GithubInfoTable.insertOrUpdate))
}
