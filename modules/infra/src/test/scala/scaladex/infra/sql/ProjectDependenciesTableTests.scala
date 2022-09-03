package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite
import scaladex.infra.sql.ProjectDependenciesTable

class ProjectDependenciesTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertOrUpdate")(check(ProjectDependenciesTable.insertOrUpdate))
  it("check deleteBySource")(check(ProjectDependenciesTable.deleteBySource))
  it("check getDependencies")(check(ProjectDependenciesTable.getDependencies))
  it("check getDependents")(check(ProjectDependenciesTable.getDependents))
  it("check countDependents")(check(ProjectDependenciesTable.countDependents))
}
