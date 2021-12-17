package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import Values._

  import ProjectTable._
  describe("should generate insert the query for") {
    it("insert") {
      val q = insert(Scalafix.project)
      check(q)
      q.sql shouldBe
        s"""INSERT INTO projects (organization, repository, created_at, github_status)
           | VALUES (?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("insertOrUpdate") {
      val q = insertOrUpdate(Scalafix.project)
      q.sql shouldBe
        s"""INSERT INTO projects (organization, repository, created_at, github_status)
           | VALUES (?, ?, ?, ?) ON CONFLICT (organization, repository) DO NOTHING""".stripMargin
          .filterNot(_ == '\n')
    }
    it("updateGithubStatus") {
      val q = updateGithubStatus()
      q.sql shouldBe
        s"""UPDATE projects SET github_status=? WHERE organization=? AND repository=?""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
