package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite

class GithubInfoTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers:
  it("check insert")(check(GithubInfoTable.insert))
  it("check insertOrUpdate")(check(GithubInfoTable.insertOrUpdate))
