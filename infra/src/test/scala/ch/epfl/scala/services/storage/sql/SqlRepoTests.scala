package ch.epfl.scala.services.storage.sql

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.ProjectDependency
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.utils.ScalaExtensions._
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
        _ <- db.insertReleases(
          Seq(Cats.core, Cats.kernel)
        ) // we don't inser Cats.laws
        _ <- db.insertDependencies(Cats.dependencies)
        directDependencies <- db.findDirectDependencies(Cats.core)
      } yield {
        directDependencies.map(_.target) should contain theSameElementsAs List(
          Some(Cats.kernel),
          None,
          None
        )
      }
    }

    it("should find reverseDependencies") {
      cleanTables() // to avoid duplicate key failures
      for {
        _ <- db
          .insertReleases(Seq(Cats.core, Cats.kernel))
        _ <- db
          .insertDependencies(Cats.dependencies)
        reverseDependencies <- db.findReverseDependencies(Cats.kernel)
      } yield {
        reverseDependencies.map(_.source) should contain theSameElementsAs List(
          Cats.core
        )
      }
    }
    it("should get all topics") {
      val topics = Set("topics1", "topics2")
      val projectWithTopics = Scalafix.projectWithGithubInfo.copy(githubInfo =
        Scalafix.projectWithGithubInfo.githubInfo.map(_.copy(topics = topics))
      )
      for {
        _ <- db.insertOrUpdateProject(projectWithTopics)
        res <- db.getAllTopics()
      } yield res shouldBe topics.toList
    }
    it("should getProjectWithDependentUponProjects") {
      def fakeDependency(r: NewRelease): ReleaseDependency =
        ReleaseDependency(
          source = r.maven,
          target = MavenReference("fake", "fake_3", "version"),
          scope = "compile"
        )
      cleanTables() // to avoid duplicate key failures

      val projects = Seq(Cats.project, Scalafix.project, PlayJsonExtra.project)
      val data = Map(
        Cats.core -> Seq(
          ReleaseDependency(
            source = Cats.core.maven,
            target = MavenReference("fake", "fake_3", "version"),
            scope = "compile"
          )
        ), // first case: on a artifact that doesn't have a corresponding release
        Cats.kernel -> Seq(
          ReleaseDependency(
            source = Cats.kernel.maven,
            target = Cats.core.maven,
            "compile"
          )
        ), // depends on it self
        Scalafix.release -> Cats.dependencies.map(
          _.copy(source = Scalafix.release.maven)
        ), // dependencies contains two cats releases
        Cats.laws -> Seq(), // doesn't depend on anything
        PlayJsonExtra.release -> Seq(
          ReleaseDependency(
            source = PlayJsonExtra.release.maven,
            target = Scalafix.release.maven,
            "compile"
          )
        )
      )
      for {
        _ <- projects.map(db.insertProject).sequence
        _ <- db.insertReleases(data.keys.toList)
        _ <- db.insertDependencies(data.values.flatten.toList)
        projectDependencies <- db.getAllProjectDependencies()
        _ <- db.insertProjectDependencies(projectDependencies)
        mostDependentProjects <- db.getMostDependentUponProject(10)
      } yield {
        projectDependencies shouldBe Seq(
          ProjectDependency(Scalafix.reference, Cats.reference),
          ProjectDependency(Cats.reference, Cats.reference),
          ProjectDependency(PlayJsonExtra.reference, Scalafix.reference)
        )
        mostDependentProjects shouldBe List(
          Cats.project -> 2,
          Scalafix.project -> 1
        )
      }
    }
  }

}
