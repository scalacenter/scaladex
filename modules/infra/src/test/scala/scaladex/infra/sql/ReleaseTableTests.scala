package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite

class ReleaseTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExists")(check(ReleaseTable.insertIfNotExists))
}
