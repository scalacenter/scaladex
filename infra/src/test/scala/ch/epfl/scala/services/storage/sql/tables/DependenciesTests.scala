package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.services.storage.sql.Values
import doobie.scalatest.IOChecker
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class DependenciesTests
    extends AsyncFunSpec
    with Matchers
    with IOChecker
    with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = Values.xa
  val dependency: NewDependency = Values.dependency

  override def beforeAll(): Unit = db.migrate().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ReleaseTable") {
    import DependenciesTable._
    describe("insert") {
      it("should generate the query") {
        val q = insert(dependency)
        check(q)
        q.sql shouldBe
          s"""|INSERT INTO dependencies (source_groupId, source_artifactId, source_version,
              | target_groupId, target_artifactId, target_version, scope) VALUES (?, ? ,?, ?, ?, ?, ?)""".stripMargin
            .filterNot(_ == '\n')
      }
    }
  }
}
