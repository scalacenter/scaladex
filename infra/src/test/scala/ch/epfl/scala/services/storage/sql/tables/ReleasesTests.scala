package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.newModel.NewProject.{Organization, Repository}
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.{NewRelease}
import doobie.scalatest.IOChecker
import doobie.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class ReleasesTests
    extends AsyncFunSpec
    with Matchers
    with IOChecker
    with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  val release: NewRelease = NewRelease(
    MavenReference(
      "com.github.xuwei-k",
      "play-json-extra_2.10",
      "0.1.1-play2.3-M1"
    ),
    version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
    organization = Organization("xuwei-k"),
    repository = Repository("play-json-extra"),
    artifact = ArtifactName("play-json-extra"),
    target = None,
    description = None,
    released = None,
    licenses = Set(),
    isNonStandardLib = false
  )
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
             | repository, artifact, target, description, released, licenses, isNonStandardLib) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
            .filterNot(_ == '\n')
      }
    }
  }
}
