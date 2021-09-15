package ch.epfl.scala.services.storage.sql

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Success
import scala.util.Try

import ch.epfl.scala.services.storage.sql.Values
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
  val transactor = Values.xa

  override def beforeAll(): Unit = db.migrate().unsafeRunSync()

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
    it("should find releases") {
      val release = Values.release
      val insertRelease = await(db.insertRelease(release))
      insertRelease shouldBe Success(release)
      val findReleases = await(db.findReleases(release.projectRef))
      findReleases shouldBe Success(List(release))
    }
    it("should insert dependencies") {
      val dependency1 = Values.dependency
      val dependency2 = dependency1.copy(scope = "test")
      await(
        db.insertDependencies(Seq(dependency1, dependency2))
      ) shouldBe Success(2)
    }
    it("should update user project form") {
      await(
        db.updateProjectForm(project.reference, project.dataForm)
      ) shouldBe Success(())
    }
  }

  def await[A](f: Future[A]): Try[A] = Try(
    Await.result(f, Duration.Inf)
  )
}
