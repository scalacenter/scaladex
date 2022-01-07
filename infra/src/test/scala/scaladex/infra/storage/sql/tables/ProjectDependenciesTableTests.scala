package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite
import scaladex.infra.storage.sql.tables.ProjectDependenciesTable

class ProjectDependenciesTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertOrUpdate")(check(ProjectDependenciesTable.insertOrUpdate))
  it("check deleteSourceProject")(check(ProjectDependenciesTable.deleteSourceProject))
}
