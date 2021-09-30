package ch.epfl.scala.services.storage.sql

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class SqlRepoTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {

  val executorService: ExecutorService = Executors.newFixedThreadPool(1)
  override implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(executorService)

  import Values._

  describe("SqlRepo") {
    it("insert project without githubInfo") {
      for {
        _ <- db.insertProject(Scalafix.project)
        foundProject <- db.findProject(Scalafix.project.reference)
      } yield {
        foundProject shouldBe Some(Scalafix.project)
      }
    }

    it("insert project with githubInfo") {
      for {
        _ <- db.insertOrUpdateProject(Scalafix.projectWithGithubInfo)
        foundProject <- db.findProject(Scalafix.reference)
      } yield {
        foundProject shouldBe Some(Scalafix.projectWithGithubInfo)
      }
    }

    it("should find releases") {
      for {
        _ <- db.insertRelease(PlayJsonExtra.release)
        foundReleases <- db.findReleases(PlayJsonExtra.reference)
      } yield {
        foundReleases shouldBe List(PlayJsonExtra.release)
      }
    }

    it("should insert dependencies") {
      for {
        _ <- db.insertDependencies(Seq(Cats.dependency, Cats.testDependency))
      } yield succeed
    }

    it("should update user project form") {
      for {
        _ <- db.updateProjectForm(Scalafix.reference, Scalafix.dataForm)
      } yield succeed
    }

    it("should find directDependencies") {
      for {
        _ <- db.insertReleases(Seq(Cats.core, Cats.kernel))
        _ <- db.insertDependencies(Cats.dependencies)
        directDependencies <- db.findDirectDependencies(Cats.core)
      } yield {
        directDependencies.map(_.target) should contain theSameElementsAs List(
          Some(Cats.kernel),
          None
        )
      }
    }

    it("should find reverseDependencies") {
      for {
        _ <- db
          .insertReleases(Seq(Cats.core, Cats.kernel))
          .failed // ignore duplicate key failures
        _ <- db
          .insertDependencies(Cats.dependencies)
          .failed // ignore duplicate key failures
        reverseDependencies <- db.findReverseDependencies(Cats.kernel)
      } yield {
        reverseDependencies.map(_.source) should contain theSameElementsAs List(
          Cats.core
        )
      }
    }
  }
}
