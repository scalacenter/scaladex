package scaladex.infra.sql

import scaladex.infra.BaseDatabaseSuite

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class GithubInfoTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers:
  it("check insert")(check(GithubInfoTable.insert))
  it("check insertOrUpdate")(check(GithubInfoTable.insertOrUpdate))
