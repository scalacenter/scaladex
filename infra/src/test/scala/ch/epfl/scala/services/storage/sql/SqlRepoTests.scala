package ch.epfl.scala.services.storage.sql

import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.Platform.ScalaJvm
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
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
      } yield foundProject shouldBe Some(Scalafix.project)
    }

    it("insert project with githubInfo") {
      for {
        _ <- db.insertOrUpdateProject(Scalafix.projectWithGithubInfo)
        foundProject <- db.findProject(Scalafix.reference)
      } yield foundProject shouldBe Some(Scalafix.projectWithGithubInfo)
    }

    it("should find releases") {
      for {
        _ <- db.insertRelease(PlayJsonExtra.release)
        foundReleases <- db.findReleases(PlayJsonExtra.reference)
      } yield foundReleases shouldBe List(PlayJsonExtra.release)
    }

    it("should insert dependencies") {
      for {
        _ <- db.insertDependencies(Seq(CatsEffect.dependency, CatsEffect.testDependency))
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
          Seq(Cats.core_3, Cats.kernel_3)
        ) // we don't inser Cats.laws_3
        _ <- db.insertDependencies(Cats.dependencies)
        directDependencies <- db.findDirectDependencies(Cats.core_3)
      } yield directDependencies.map(_.target) should contain theSameElementsAs List(
        Some(Cats.kernel_3),
        None,
        None
      )
    }

    it("should find reverseDependencies") {
      cleanTables() // to avoid duplicate key failures
      for {
        _ <- db
          .insertReleases(Seq(Cats.core_3, Cats.kernel_3))
        _ <- db
          .insertDependencies(Cats.dependencies)
        reverseDependencies <- db.findReverseDependencies(Cats.kernel_3)
      } yield reverseDependencies.map(_.source) should contain theSameElementsAs List(Cats.core_3)
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
    it("should get most dependent project") {
      cleanTables() // to avoid duplicate key failures

      val projects = Seq(Cats.project, Scalafix.project, PlayJsonExtra.project)
      val data = Map(
        Cats.core_3 -> Seq(
          ReleaseDependency(
            source = Cats.core_3.maven,
            target = MavenReference("fake", "fake_3", "version"),
            scope = "compile"
          )
        ), // first case: on a artifact that doesn't have a corresponding release
        Cats.kernel_3 -> Seq(
          ReleaseDependency(
            source = Cats.kernel_3.maven,
            target = Cats.core_3.maven,
            "compile"
          )
        ), // depends on it self
        Scalafix.release -> Cats.dependencies.map(
          _.copy(source = Scalafix.release.maven)
        ), // dependencies contains two cats releases
        Cats.laws_3 -> Seq(), // doesn't depend on anything
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
        projectDependencies <- db.computeProjectDependencies()
        _ <- db.insertProjectDependencies(projectDependencies)
        mostDependentProjects <- db.getMostDependentUponProject(10)
      } yield {
        projectDependencies shouldBe Seq(
          ProjectDependency(Scalafix.reference, Cats.reference),
          ProjectDependency(Cats.reference, Cats.reference),
          ProjectDependency(PlayJsonExtra.reference, Scalafix.reference)
        )
        mostDependentProjects shouldBe List(Cats.project -> 2, Scalafix.project -> 1)
      }
    }
    it("should updateCreated") {
      val now = Instant.now
      val newProject = NewProject.defaultProject("org", "repo") // created_at = None
      val oneRelease = NewRelease(
        MavenReference(
          "something",
          "something",
          "0.1.1-play2.3-M1"
        ),
        version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
        organization = NewProject.Organization("org"),
        repository = NewProject.Repository("repo"),
        artifactName = ArtifactName("something"),
        platform = ScalaJvm(ScalaVersion.`2.11`),
        description = None,
        releasedAt = Some(now),
        resolver = None,
        licenses = Set(),
        isNonStandardLib = false
      )
      for {
        _ <- db.insertReleases(Seq(oneRelease))
        _ <- db.insertProject(newProject)
        _ <- db.updateCreatedInProjects()
        res <- db.findProject(newProject.reference)
      } yield res.get.created.get shouldBe now
    }
  }
}
