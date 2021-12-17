package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("insertOrUpdateRef") {
    check(ProjectTable.insertIfNotExists)
  }

  it("updateGithubStatus") {
    ProjectTable.updateGithubStatus.sql shouldBe
      s"""UPDATE projects SET github_status=? WHERE organization=? AND repository=?""".stripMargin
        .filterNot(_ == '\n')
  }
}
