package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite

class ReleaseDependenciesTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExists")(check(ReleaseDependenciesTable.insertIfNotExists))
  it("check getDirectDependencies")(check(ReleaseDependenciesTable.getDirectDependencies))
  it("check getReverseDependencies")(check(ReleaseDependenciesTable.getReverseDependencies))
}
