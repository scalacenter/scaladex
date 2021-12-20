package ch.epfl.scala.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.Project
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
    it("insert release and its dependencies") {
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        project <- db.findProject(Cats.reference)
        releases <- db.findReleases(Cats.reference)
        dependencies <- db.findDependencies(Cats.core_3)
      } yield {
        project should not be empty
        releases should contain theSameElementsAs Seq(Cats.core_3)
        dependencies should contain theSameElementsAs Cats.dependencies
      }
    }

    it("should find releases") {
      for {
        _ <- db.insertRelease(PlayJsonExtra.release, Seq.empty, now)
        foundReleases <- db.findReleases(PlayJsonExtra.reference)
      } yield foundReleases shouldBe List(PlayJsonExtra.release)
    }

    it("should update user project form") {
      for {
        _ <- db.insertRelease(Scalafix.release, Seq.empty, now)
        _ <- db.updateProjectForm(Scalafix.reference, Scalafix.dataForm)
      } yield succeed
    }

    it("should find directDependencies") {
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        _ <- db.insertRelease(Cats.kernel_3, Seq.empty, now)
        directDependencies <- db.findDirectDependencies(Cats.core_3)
      } yield directDependencies.map(_.target) should contain theSameElementsAs List(Some(Cats.kernel_3), None, None)
    }

    it("should find reverseDependencies") {
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        _ <- db.insertRelease(Cats.kernel_3, Seq.empty, now)
        reverseDependencies <- db.findReverseDependencies(Cats.kernel_3)
      } yield reverseDependencies.map(_.source) should contain theSameElementsAs List(Cats.core_3)
    }
    it("should get all topics") {
      for {
        _ <- db.insertRelease(Scalafix.release, Seq.empty, now)
        _ <- db.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, Scalafix.githubStatus)
        res <- db.getAllTopics()
      } yield res should contain theSameElementsAs Scalafix.githubInfo.topics
    }
    it("should get most dependent project") {
      val releases: Map[NewRelease, Seq[ReleaseDependency]] = Map(
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
        _ <- releases.map { case (release, dependencies) => db.insertRelease(release, dependencies, now) }.sequence
        projectDependencies <- db.computeProjectDependencies()
        _ <- db.insertProjectDependencies(projectDependencies)
        mostDependentProjects <- db.getMostDependentUponProject(10)
      } yield {
        projectDependencies shouldBe Seq(
          ProjectDependency(Scalafix.reference, Cats.reference),
          ProjectDependency(Cats.reference, Cats.reference),
          ProjectDependency(PlayJsonExtra.reference, Scalafix.reference)
        )
        mostDependentProjects.map { case (project, deps) => (project.reference, deps) } shouldBe List(
          Cats.reference -> 2,
          Scalafix.reference -> 1
        )
      }
    }
    it("should updateCreated") {
      for {
        _ <- db.insertRelease(Scalafix.release, Seq.empty, now)
        projectsCreationDate <- db.computeAllProjectsCreationDate()
        _ <- projectsCreationDate.mapSync {
          case (creationDate, ref) => db.updateProjectCreationDate(ref, creationDate)
        }
        res <- db.findProject(Scalafix.reference)
      } yield res.get.created.get shouldBe Scalafix.release.releasedAt.get
    }
    it("should createMovedProject") {
      val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
      val newRef = Project.Reference.from("scala", "fix")
      val newGithubInfo =
        Scalafix.githubInfo.copy(owner = newRef.organization.value, name = newRef.repository.value, stars = Some(10000))
      val moved = GithubStatus.Moved(now, newRef.organization, newRef.repository)
      for {
        _ <- db.insertRelease(Scalafix.release, Seq.empty, now)
        _ <- db.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, Scalafix.githubStatus)
        _ <- db.createMovedProject(Scalafix.reference, newGithubInfo, moved)
        newProject <- db.findProject(newRef)
        oldProject <- db.findProject(Scalafix.reference)
      } yield {
        oldProject.get.githubStatus shouldBe moved
        newProject.get.reference shouldBe newRef
        newProject.get.githubStatus shouldBe GithubStatus.Ok(now)
      }
    }
  }
}
