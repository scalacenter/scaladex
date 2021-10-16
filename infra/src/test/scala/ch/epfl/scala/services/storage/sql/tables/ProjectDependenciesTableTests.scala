package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectDependenciesTableTests
    extends AsyncFunSpec
    with BaseDatabaseSuite
    with Matchers {
  import Values._

  import ProjectDependenciesTable._
  describe("should generate insert the query for") {
    it("insertOrUpdate") {
      val q = insertOrUpdate(
        ProjectDependency(Scalafix.reference, PlayJsonExtra.reference)
      )
      check(q)
      q.sql shouldBe
        s"""INSERT INTO project_dependencies (source_organization, source_repository,
           | target_organization, target_repository) VALUES (?, ?, ?, ?)
           | ON CONFLICT (source_organization, source_repository,
           | target_organization, target_repository) DO NOTHING""".stripMargin
          .filterNot(_ == '\n')
    }
    it("getMostDependentUponProjects") {
      val q = getMostDependentUponProjects(1)
      check(q)
      q.sql shouldBe
        s"""SELECT target_organization, target_repository,
           | Count(DISTINCT (source_organization, source_repository)) as total
           | FROM project_dependencies
           | GROUP BY target_organization, target_repository
           | ORDER BY total DESC LIMIT ?""".stripMargin
          .filterNot(_ == '\n')
    }
  }

}
