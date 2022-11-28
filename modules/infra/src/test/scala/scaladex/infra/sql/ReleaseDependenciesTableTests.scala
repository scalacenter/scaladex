package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.DatabaseSuite

class ReleaseDependenciesTableTests extends AnyFunSpec with DatabaseSuite with Matchers {
  it("check insertIfNotExists")(check(ReleaseDependenciesTable.insertIfNotExists))
}
