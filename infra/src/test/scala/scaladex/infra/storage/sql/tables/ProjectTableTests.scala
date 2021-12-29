package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite
import scaladex.infra.storage.sql.tables.ProjectTable

class ProjectTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExists")(check(ProjectTable.insertIfNotExists))
  it("check updateGithubStatus")(check(ProjectTable.updateGithubStatus))
  it("check selectProjects")(check(ProjectTable.selectProject))
  it("check selectReferenceAndStatus")(check(ProjectTable.selectReferenceAndStatus))
  it("check updateCreated")(check(ProjectTable.updateCreationDate))
  it("check selectLatestProjects")(check(ProjectTable.selectLatestProjects(5)))
}
