package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ReleaseTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import Values._

  import ReleaseTable._
  describe("should generate the query") {
    it("insert") {
      check(ReleaseTable.insert)
      ReleaseTable.insert.sql shouldBe
        s"""INSERT INTO releases (groupId, artifactId, version, organization,
           | repository, artifact, platform, description, released_at, resolver,
           | licenses, isNonStandardLib) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("selectReleases") {
      val q = selectReleases(PlayJsonExtra.reference)
      check(q)
      q.sql shouldBe
        s"""SELECT * FROM releases WHERE organization=? AND repository=?""".stripMargin
          .filterNot(_ == '\n')
    }
    it("findOldestRelease") {
      val q = findOldestReleasesPerProjectReference()
      check(q)
      q.sql shouldBe
        s"""SELECT min(released_at) as oldest_release, organization, repository
           | FROM releases where released_at IS NOT NULL
           | group by organization, repository""".stripMargin
          .filterNot(_ == '\n')
    }
    it("updateProjectRef") {
      val q = updateProjectRef()
      check(q)
      q.sql shouldBe
        s"""UPDATE releases SET organization=?, repository=?
           | WHERE groupId=? AND artifactId=? AND version=?""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
