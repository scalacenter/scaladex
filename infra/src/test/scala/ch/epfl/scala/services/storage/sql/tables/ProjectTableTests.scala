package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import doobie.scalatest.IOChecker
import ch.epfl.scala.index.newModel.NewProject
import org.scalatest.BeforeAndAfterAll

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectTableTests
    extends AsyncFunSpec
    with Matchers
    with IOChecker
    with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  private val project = NewProject.defaultProject("scalacenter", "scaladex")

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ProjectTable") {
    import ProjectTable._
    describe("insert") {
      it("should generate the query") {
        val q = insert(project)
        check(q)
        q.sql shouldBe
          s"""INSERT INTO projects (organization, repository,
             | defaultStableVersion, defaultArtifact, strictVersions,
             | customScalaDoc, documentationLinks, deprecated, contributorsWanted,
             | artifactDeprecations, cliArtifacts, primaryTopic, esId)
             | VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
            .filterNot(_ == '\n')
      }
    }
  }

}
