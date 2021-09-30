package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ReleaseTableTests
    extends AsyncFunSpec
    with BaseDatabaseSuite
    with Matchers {
  import Values._

  describe("ReleaseTable") {
    import ReleaseTable._
    describe("insert") {
      it("should generate the query") {
        val q = insert(PlayJsonExtra.release)
        check(q)
        q.sql shouldBe
          s"""INSERT INTO releases (groupId, artifactId, version, organization,
             | repository, artifact, target, description, released, resolver,
             | licenses, isNonStandardLib) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
            .filterNot(_ == '\n')
      }
    }
  }
}
