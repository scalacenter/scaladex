package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite

class ProjectUserFormTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import scaladex.infra.Values._

  import scaladex.infra.storage.sql.tables.ProjectUserFormTable._
  describe("should generate query for") {
    it("insertOrUpdate") {
      val q = insertOrUpdate(Scalafix.reference)(Scalafix.dataForm)
      q.sql shouldBe
        s"""INSERT INTO project_user_data (organization, repository, defaultStableVersion, defaultArtifact,
           | strictVersions, customScalaDoc, documentationLinks, deprecated, contributorsWanted,
           | artifactDeprecations, cliArtifacts, primaryTopic, beginnerIssuesLabel) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           | ON CONFLICT (organization, repository) DO NOTHING""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
