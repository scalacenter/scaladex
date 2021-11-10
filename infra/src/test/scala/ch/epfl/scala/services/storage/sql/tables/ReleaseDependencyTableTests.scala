package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ReleaseDependencyTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import Values._

  import ReleaseDependencyTable._
  describe("should generate the query for") {
    it("insert") {
      val q = insert(Cats.dependency)
      check(q)
      q.sql shouldBe
        s"""|INSERT INTO release_dependencies (source_groupId, source_artifactId, source_version,
            | target_groupId, target_artifactId, target_version, scope) VALUES (?, ? ,?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("find") {
      val q = find(Cats.dependency.source)
      check(q)
      q.sql shouldBe
        s"""|SELECT source_groupId, source_artifactId, source_version, target_groupId,
            | target_artifactId, target_version, scope
            | FROM release_dependencies WHERE source_groupId=? AND source_artifactId=? AND source_version=?""".stripMargin
          .filterNot(_ == '\n')
    }

    it("selectDirectDependencies") {
      val q = selectDirectDependencies(PlayJsonExtra.release)
      check(q)
      q.sql shouldBe
        s"""|SELECT d.source_groupId, d.source_artifactId, d.source_version, d.target_groupId, d.target_artifactId, d.target_version, d.scope,
            | r.groupId, r.artifactId, r.version, r.organization, r.repository, r.artifact,
            | r.platform, r.description, r.released_at, r.resolver, r.licenses, r.isNonStandardLib
            | FROM release_dependencies d LEFT JOIN releases r ON d.target_groupid = r.groupid AND
            | d.target_artifactid = r.artifactid AND
            | d.target_version = r.version
            | WHERE d.source_groupId=? AND d.source_artifactId=? AND d.source_version=?""".stripMargin
          .filterNot(_ == '\n')
    }

    it("selectReverseDependencies") {
      val q = selectReverseDependencies(PlayJsonExtra.release)
      check(q)
      q.sql shouldBe
        s"""|SELECT d.source_groupId, d.source_artifactId, d.source_version, d.target_groupId, d.target_artifactId, d.target_version, d.scope,
            | r.groupId, r.artifactId, r.version, r.organization, r.repository, r.artifact,
            | r.platform, r.description, r.released_at, r.resolver, r.licenses, r.isNonStandardLib
            | FROM release_dependencies d INNER JOIN releases r ON d.source_groupid = r.groupid AND
            | d.source_artifactid = r.artifactid AND
            | d.source_version = r.version
            | WHERE d.target_groupId=? AND d.target_artifactId=? AND d.target_version=?""".stripMargin
          .filterNot(_ == '\n')
    }
    it("getAllProjectDependencies") {
      val q = getAllProjectDependencies()
      check(q)
      q.sql shouldBe
        s"""|SELECT DISTINCT d.organization, d.repository, t.organization, t.repository
            | FROM ( release_dependencies d INNER JOIN releases r ON d.source_groupid = r.groupid AND
            | d.source_artifactid = r.artifactid AND d.source_version = r.version) d
            | INNER JOIN releases t ON d.target_groupid = t.groupid AND d.target_artifactid = t.artifactid AND
            | d.target_version = t.version GROUP BY d.organization, d.repository, t.organization, t.repository""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
