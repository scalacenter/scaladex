package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  import Values._

  import ArtifactTable._
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
