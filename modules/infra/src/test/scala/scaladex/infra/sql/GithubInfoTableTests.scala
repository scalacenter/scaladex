package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.DatabaseSuite
import scaladex.infra.sql.GithubInfoTable

class GithubInfoTableTests extends AnyFunSpec with DatabaseSuite with Matchers {
  it("check insert")(check(GithubInfoTable.insert))
  it("check insertOrUpdate")(check(GithubInfoTable.insertOrUpdate))
}
