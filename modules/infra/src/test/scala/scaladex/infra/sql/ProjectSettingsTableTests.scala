package scaladex.infra.sql

import scaladex.infra.BaseDatabaseSuite

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectSettingsTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers:
  it("check insertOrUpdate")(check(ProjectSettingsTable.insertOrUpdate))
