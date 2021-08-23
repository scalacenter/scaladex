package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import ch.epfl.scala.services.storage.sql.Values
import doobie.scalatest.IOChecker
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectUserFormTableTests
    extends AsyncFunSpec
    with Matchers
    with IOChecker
    with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  val project = Values.projectWithGithubInfo
  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ProjectUserFormTable") {
    import ProjectUserFormTable._
    it("should generate insert the query") {
      val q = insert(project)(project.formData)
      check(q)
      q.sql shouldBe
        s"""INSERT INTO project_user_data (organization, repository, defaultStableVersion,
           | defaultArtifact, strictVersions, customScalaDoc, documentationLinks, deprecated,
           | contributorsWanted, artifactDeprecations, cliArtifacts, primaryTopic)
           | VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("insert Or Update") {
      val q = insertOrUpdate(project)(project.formData)
      q.sql shouldBe
        s"""INSERT INTO project_user_data (organization, repository, defaultStableVersion, defaultArtifact,
           | strictVersions, customScalaDoc, documentationLinks, deprecated, contributorsWanted,
           | artifactDeprecations, cliArtifacts, primaryTopic) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           | ON CONFLICT (organization, repository) DO NOTHING""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
