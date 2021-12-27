package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite
import scaladex.infra.storage.sql.tables.ArtifactTable

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  import scaladex.core.test.Values._

  import scaladex.infra.storage.sql.tables.ArtifactTable._
  describe("should generate the query") {
    it("check insert") {
      check(ArtifactTable.insert)
    }
    it("selectArtifacts") {
      val q = selectArtifacts(PlayJsonExtra.reference)
      check(q)
      q.sql shouldBe
        s"""SELECT * FROM artifacts WHERE organization=? AND repository=?""".stripMargin
          .filterNot(_ == '\n')
    }
    it("check selectArtifacts by name") {
      val q = selectArtifacts(Cats.reference, Cats.core_3.artifactName)
      check(q)
    }
    it("check findOldestArtifactsPerProjectReference") {
      val q = findOldestArtifactsPerProjectReference()
      check(q)
    }
    it("updateProjectRef") {
      val q = updateProjectRef
      check(q)
      q.sql shouldBe
        s"""UPDATE artifacts SET organization=?, repository=?
           | WHERE group_id=? AND artifact_id=? AND version=?""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
