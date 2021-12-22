package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExists") {
    check(ProjectTable.insertIfNotExists)
  }

  it("check updateGithubStatus") {
    check(ProjectTable.updateGithubStatus)
  }

  it("check selectAllProjects") {
    check(ProjectTable.selectAllProjects)
  }
}
