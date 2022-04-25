package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite
import scaladex.infra.sql.ProjectDependenciesTable

class ProjectDependenciesTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertOrUpdate")(check(ProjectDependenciesTable.insertOrUpdate))
  it("check deleteBySource")(check(ProjectDependenciesTable.deleteBySource))
  it("check deleteByTarget")(check(ProjectDependenciesTable.deleteByTarget))
  it("check getInverseDependencies")(check(ProjectDependenciesTable.getInverseDependencies))
  it("check getDirectDependencies")(check(ProjectDependenciesTable.getDirectDependencies))
}
