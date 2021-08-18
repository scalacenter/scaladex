package ch.epfl.scala.services.storage.sql.tables

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Success
import scala.util.Try

import cats.effect.IO
import doobie.scalatest.IOChecker
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class SqlRepoTests
    extends AsyncFunSpec
    with Matchers
    with IOChecker
    with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  private val project = Values.project
  private val projectWithGithubInfo = Values.projectWithGithubInfo

  describe("SqlRepo") {
    it("insert project without githubInfo") {
      val insertProject = await(db.insertProject(project))
      insertProject shouldBe Success(())
      val findProject =
        await(db.findProject(project.reference))
      findProject shouldBe Success(Some(project))
    }
    it("insert project with githubInfo") {
      val insertProject = await(db.insertProject(projectWithGithubInfo))
      insertProject shouldBe Success(())
      val findProject =
        await(db.findProject(projectWithGithubInfo.reference))
      findProject shouldBe Success(Some(projectWithGithubInfo))
    }
  }

  def await[A](f: Future[A]): Try[A] = Try(
    Await.result(f, Duration.Inf)
  )
}
