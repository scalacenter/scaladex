package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectTableTests
    extends AsyncFunSpec
    with BaseDatabaseSuite
    with Matchers {
  import Values._

  describe("ProjectTable") {
    import ProjectTable._
    it("should generate insert the query") {
      val q = insert(Scalafix.project)
      check(q)
      q.sql shouldBe
        s"""INSERT INTO projects (organization, repository, esId)
           | VALUES (?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("insert Or Update") {
      val q = insertOrUpdate(Scalafix.project)
      q.sql shouldBe
        s"""INSERT INTO projects (organization, repository, esId)
           | VALUES (?, ?, ?) ON CONFLICT (organization, repository) DO NOTHING""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
