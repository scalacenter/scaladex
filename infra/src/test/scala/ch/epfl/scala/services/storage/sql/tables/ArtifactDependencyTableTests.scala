package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactDependencyTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import Values._

  import ArtifactDependencyTable._
  describe("should generate the query for") {
    it("insert") {
      check(ArtifactDependencyTable.insert)
      ArtifactDependencyTable.insert.sql shouldBe
        s"""|INSERT INTO artifact_dependencies (source_group_id, source_artifact_id, source_version,
            | target_group_id, target_artifact_id, target_version, scope) VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("find") {
      val q = find(CatsEffect.dependency.source)
      check(q)
      q.sql shouldBe
        s"""|SELECT source_group_id, source_artifact_id, source_version, target_group_id,
            | target_artifact_id, target_version, scope
            | FROM artifact_dependencies WHERE source_group_id=? AND source_artifact_id=? AND source_version=?""".stripMargin
          .filterNot(_ == '\n')
    }

    it("selectDirectDependencies") {
      val q = selectDirectDependencies(PlayJsonExtra.artifact)
      check(q)
      q.sql shouldBe
        s"""|SELECT d.source_group_id, d.source_artifact_id, d.source_version, d.target_group_id, d.target_artifact_id, d.target_version, d.scope,
            | a.group_id, a.artifact_id, a.version, a.artifact_name, a.platform, a.organization, a.repository,
            | a.description, a.released_at, a.resolver, a.licenses, a.isNonStandardLib
            | FROM artifact_dependencies d LEFT JOIN artifacts a ON d.target_group_id = a.group_id AND
            | d.target_artifact_id = a.artifact_id AND
            | d.target_version = a.version
            | WHERE d.source_group_id=? AND d.source_artifact_id=? AND d.source_version=?""".stripMargin
          .filterNot(_ == '\n')
    }

    it("selectReverseDependencies") {
      val q = selectReverseDependencies(PlayJsonExtra.artifact)
      check(q)
      q.sql shouldBe
        s"""|SELECT d.source_group_id, d.source_artifact_id, d.source_version, d.target_group_id, d.target_artifact_id, d.target_version, d.scope,
            | a.group_id, a.artifact_id, a.version, a.artifact_name, a.platform, a.organization, a.repository,
            | a.description, a.released_at, a.resolver, a.licenses, a.isNonStandardLib
            | FROM artifact_dependencies d INNER JOIN artifacts a ON d.source_group_id = a.group_id AND
            | d.source_artifact_id = a.artifact_id AND
            | d.source_version = a.version
            | WHERE d.target_group_id=? AND d.target_artifact_id=? AND d.target_version=?""".stripMargin
          .filterNot(_ == '\n')
    }
    it("getAllProjectDependencies") {
      val q = getAllProjectDependencies()
      check(q)
      q.sql shouldBe
        s"""|SELECT DISTINCT d.organization, d.repository, t.organization, t.repository
            | FROM ( artifact_dependencies d INNER JOIN artifacts a ON d.source_group_id = a.group_id AND
            | d.source_artifact_id = a.artifact_id AND d.source_version = a.version) d
            | INNER JOIN artifacts t ON d.target_group_id = t.group_id AND d.target_artifact_id = t.artifact_id AND
            | d.target_version = t.version GROUP BY d.organization, d.repository, t.organization, t.repository""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}