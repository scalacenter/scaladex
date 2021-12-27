package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite

class ProjectSettingsTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  import scaladex.core.test.Values._

  describe("should generate query for") {
    it("check insertOrUpdate") {
      val q = ProjectSettingsTable.insertOrUpdate(Scalafix.reference)(Scalafix.settings)
      check(q)
    }
  }
}
