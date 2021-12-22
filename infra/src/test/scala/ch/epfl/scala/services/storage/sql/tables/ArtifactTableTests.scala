package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import Values._

  import ArtifactTable._
  describe("should generate the query") {
    it("insert") {
      check(ArtifactTable.insert)
      ArtifactTable.insert.sql shouldBe
        s"""|INSERT INTO artifacts (group_id, artifact_id, version, artifact_name, platform,
            | organization, repository, description, released_at, resolver,
            | licenses, isNonStandardLib) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("selectArtifacts") {
      val q = selectArtifacts(PlayJsonExtra.reference)
      check(q)
      q.sql shouldBe
        s"""SELECT * FROM artifacts WHERE organization=? AND repository=?""".stripMargin
          .filterNot(_ == '\n')
    }
    it("findOldestArtifactsPerProjectReference") {
      val q = findOldestArtifactsPerProjectReference()
      check(q)
      q.sql shouldBe
        s"""SELECT min(released_at) as oldest_artifact, organization, repository
           | FROM artifacts where released_at IS NOT NULL
           | group by organization, repository""".stripMargin
          .filterNot(_ == '\n')
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
