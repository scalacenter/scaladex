package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite

class ProjectSettingsTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  describe("should generate query for") {
    it("check insertOrUpdate")(check(ProjectSettingsTable.insertOrUpdate))
  }
}
