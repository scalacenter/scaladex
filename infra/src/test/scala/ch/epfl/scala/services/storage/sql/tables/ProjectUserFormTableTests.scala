package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectUserFormTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import Values._

  import ProjectUserFormTable._
  describe("should generate query for") {
    it("insert") {
      val q = insert(Scalafix.project)(Scalafix.dataForm)
      check(q)
      q.sql shouldBe
        s"""INSERT INTO project_user_data (organization, repository, defaultStableVersion,
           | defaultArtifact, strictVersions, customScalaDoc, documentationLinks, deprecated,
           | contributorsWanted, artifactDeprecations, cliArtifacts, primaryTopic)
           | VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("insertOrUpdate") {
      val q = insertOrUpdate(Scalafix.project)(Scalafix.dataForm)
      q.sql shouldBe
        s"""INSERT INTO project_user_data (organization, repository, defaultStableVersion, defaultArtifact,
           | strictVersions, customScalaDoc, documentationLinks, deprecated, contributorsWanted,
           | artifactDeprecations, cliArtifacts, primaryTopic) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           | ON CONFLICT (organization, repository) DO NOTHING""".stripMargin
          .filterNot(_ == '\n')
    }
    it("update user data") {
      val q = update(Scalafix.project)(Scalafix.dataForm)
      q.sql shouldBe
        s"""|UPDATE project_user_data SET defaultStableVersion=?, defaultArtifact=?,
            | strictVersions=?, customScalaDoc=?, documentationLinks=?, deprecated=?,
            | contributorsWanted=?, artifactDeprecations=?, cliArtifacts=?, primaryTopic=?
            | WHERE organization=? AND repository=?
            |""".stripMargin.filterNot(_ == '\n')
    }
  }
}
