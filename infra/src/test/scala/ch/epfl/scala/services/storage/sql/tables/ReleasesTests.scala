package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import doobie.scalatest.IOChecker
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ReleasesTests
    extends AsyncFunSpec
    with Matchers
    with IOChecker
    with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  val release = Values.release

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ReleaseTable") {
    import ReleaseTable._
    describe("insert") {
      it("should generate the query") {
        val q = insert(release)
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
