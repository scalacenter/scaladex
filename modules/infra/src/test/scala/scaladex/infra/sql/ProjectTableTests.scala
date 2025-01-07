package scaladex.infra.sql

import scaladex.infra.BaseDatabaseSuite

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers:
  it("check insertIfNotExists")(check(ProjectTable.insertIfNotExists))
  it("check updateCreationDate")(check(ProjectTable.updateCreationDate))
  it("check updateGithubStatus")(check(ProjectTable.updateGithubStatus))
  it("check selectByReference")(check(ProjectTable.selectByReference))
  it("check selectByNewReferecnce")(check(ProjectTable.selectByNewReference))
  it("check selectReferenceAndStatus")(check(ProjectTable.selectReferenceAndStatus))
  it("check selectProject")(check(ProjectTable.selectProject))
  it("check selectProjectByGithubStatus")(check(ProjectTable.selectProjectByGithubStatus))
