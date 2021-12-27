package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.ProjectDependency
import scaladex.infra.storage.sql.BaseDatabaseSuite

class ProjectDependenciesTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import scaladex.core.test.Values._

  import scaladex.infra.storage.sql.tables.ProjectDependenciesTable._
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
