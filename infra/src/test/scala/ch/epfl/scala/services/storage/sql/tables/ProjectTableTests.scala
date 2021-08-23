package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import ch.epfl.scala.services.storage.sql.Values
import doobie.scalatest.IOChecker
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
  private val project = Values.project

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ProjectTable") {
    import ProjectTable._
    it("should generate insert the query") {
      val q = insert(project)
      check(q)
      q.sql shouldBe
        s"""INSERT INTO projects (organization, repository, esId)
           | VALUES (?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("insert Or Update") {
      val q = insertOrUpdate(project)
      q.sql shouldBe
        s"""INSERT INTO projects (organization, repository, esId)
           | VALUES (?, ?, ?) ON CONFLICT (organization, repository) DO NOTHING""".stripMargin
          .filterNot(_ == '\n')
    }
  }
}
