package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.DatabaseSuite

class UserSessionsTableTests extends AnyFunSpec with DatabaseSuite with Matchers {
  it("check insertOrUpdate")(check(UserSessionsTable.insertOrUpdate))
  it("check selectUserSessionById")(check(UserSessionsTable.selectUserSessionById))
  it("check selectAllUserSessions")(check(UserSessionsTable.selectAllUserSessions))
  it("check deleteByUserId")(check(UserSessionsTable.deleteByUserId))
}
