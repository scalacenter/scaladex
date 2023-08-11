package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite

class UserSessionsTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insert")(check(UserSessionsTable.insert))
  it("check update")(check(UserSessionsTable.update))
  it("check selectById")(check(UserSessionsTable.selectById))
  it("check selectAll")(check(UserSessionsTable.selectAll))
  it("check deleteById")(check(UserSessionsTable.deleteById))
}
