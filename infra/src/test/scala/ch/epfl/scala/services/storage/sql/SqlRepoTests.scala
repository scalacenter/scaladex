package ch.epfl.scala.services.storage.sql

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.ArtifactDependency
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.ProjectDependency
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
      } yield {
        project should not be empty
        releases should contain theSameElementsAs Seq(Cats.core_3)
      }
    }

    it("should get all project references") {
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        _ <- db.insertRelease(PlayJsonExtra.artifact, Seq.empty, now)
        foundReferences <- db.getAllProjectRef()
      } yield foundReferences should contain theSameElementsAs Seq(PlayJsonExtra.reference, Cats.reference)
    }

    it("should get all projects") {
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        _ <- db.updateGithubInfoAndStatus(Cats.reference, Cats.githubInfo, GithubStatus.Ok(now))
        _ <- db.insertRelease(Scalafix.artifact, Seq.empty, now)
        _ <- db.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
        _ <- db.updateProjectForm(Scalafix.reference, Scalafix.dataForm)
        allProjects <- db.getAllProjects()
      } yield allProjects should contain theSameElementsAs Seq(Cats.project, Scalafix.project)
    }

    it("should update artifacts") {
      val newRef = Project.Reference.from("kindlevel", "dogs")
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        _ <- db.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
        _ <- db.updateReleases(Seq(Cats.core_3, Cats.core_sjs1_3), newRef)
        oldReleases <- db.findReleases(Cats.reference)
        movedReleases <- db.findReleases(newRef)
      } yield {
        oldReleases shouldBe empty
        movedReleases should contain theSameElementsAs Seq(
          Cats.core_3.copy(projectRef = newRef),
          Cats.core_sjs1_3.copy(projectRef = newRef)
        )
      }
    }

    it("should update github status") {
      val failed = GithubStatus.Failed(now, 405, "Unauthorized")
      for {
        _ <- db.insertRelease(Scalafix.artifact, Seq.empty, now)
        _ <- db.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
        _ <- db.updateGithubStatus(Scalafix.reference, failed)
        scalafix <- db.findProject(Scalafix.reference)
      } yield scalafix.get.githubStatus shouldBe failed
    }

    it("should find artifacts by name") {
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        _ <- db.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
        artifacts <- db.findReleases(Cats.reference, Cats.core_3.artifactName)
      } yield artifacts should contain theSameElementsAs Seq(Cats.core_3, Cats.core_sjs1_3)
    }

    it("should count projects, artifacts, dependencies, github infos and data forms") {
      for {
        _ <- db.insertRelease(Cats.core_3, Cats.dependencies, now)
        _ <- db.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
        _ <- db.insertRelease(Scalafix.artifact, Seq.empty, now)
        _ <- db.insertRelease(PlayJsonExtra.artifact, Seq.empty, now)
        _ <- db.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
        _ <- db.updateProjectForm(Scalafix.reference, Scalafix.dataForm)
        projects <- db.countProjects()
        artifacts <- db.countArtifacts()
        dependencies <- db.countDependencies()
        githubInfos <- db.countGithubInfo()
        dataForms <- db.countProjectDataForm()
      } yield {
        projects shouldBe 3L
        artifacts shouldBe 4L
        dependencies shouldBe 3L
        githubInfos shouldBe 1L
        dataForms shouldBe 1L
      }
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
        _ <- db.insertRelease(Scalafix.artifact, Seq.empty, now)
        _ <- db.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
        res <- db.getAllTopics()
      } yield res should contain theSameElementsAs Scalafix.githubInfo.topics
    }

    it("should get all platforms") {
      for {
        _ <- db.insertRelease(Cats.core_3, Seq.empty, now)
        _ <- db.insertRelease(Cats.core_native04_213, Seq.empty, now)
        _ <- db.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
        _ <- db.insertRelease(Cats.core_sjs06_213, Seq.empty, now)
        res <- db.getAllPlatforms()
      } yield res(Cats.reference) should contain theSameElementsAs Set(
        Cats.core_3.platform,
        Cats.core_native04_213.platform,
        Cats.core_sjs1_3.platform,
        Cats.core_sjs06_213.platform
      )
    }

    it("should get most dependent project") {
      val releases: Map[Artifact, Seq[ArtifactDependency]] = Map(
        Cats.core_3 -> Seq(
          ArtifactDependency(
            source = Cats.core_3.mavenReference,
            target = Artifact.MavenReference("fake", "fake_3", "version"),
            scope = "compile"
          )
        ), // first case: on a artifact that doesn't have a corresponding release
        Cats.kernel_3 -> Seq(
          ArtifactDependency(
            source = Cats.kernel_3.mavenReference,
            target = Cats.core_3.mavenReference,
            "compile"
          )
        ), // depends on it self
        Scalafix.artifact -> Cats.dependencies.map(
          _.copy(source = Scalafix.artifact.mavenReference)
        ), // dependencies contains two cats releases
        Cats.laws_3 -> Seq(), // doesn't depend on anything
        PlayJsonExtra.artifact -> Seq(
          ArtifactDependency(
            source = PlayJsonExtra.artifact.mavenReference,
            target = Scalafix.artifact.mavenReference,
            "compile"
          )
        )
      )
      for {
        _ <- releases.map { case (release, dependencies) => db.insertRelease(release, dependencies, now) }.sequence
        projectDependencies <- db.computeProjectDependencies()
        _ <- db.insertProjectDependencies(projectDependencies)
        catsInverseDependencies <- db.countInverseProjectDependencies(Cats.reference)
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
        catsInverseDependencies shouldBe 2
      }
    }
    it("should update creation date and get latest project") {
      for {
        _ <- db.insertRelease(Scalafix.artifact, Seq.empty, now)
        _ <- db.insertRelease(Cats.core_3, Seq.empty, now)
        _ <- db.insertRelease(PlayJsonExtra.artifact, Seq.empty, now)
        creationDates <- db.computeAllProjectsCreationDate()
        _ <- creationDates.mapSync { case (creationDate, ref) => db.updateProjectCreationDate(ref, creationDate) }
        latestProject <- db.getLatestProjects(2)
      } yield (latestProject.map(p => p.reference -> p.creationDate) should contain).theSameElementsInOrderAs(
        Seq(
          Cats.reference -> Cats.core_3.releaseDate,
          Scalafix.reference -> Scalafix.artifact.releaseDate
        )
      )
    }
    it("should createMovedProject") {
      val newRef = Project.Reference.from("scala", "fix")
      val newGithubInfo =
        Scalafix.githubInfo.copy(owner = newRef.organization.value, name = newRef.repository.value, stars = Some(10000))
      val moved = GithubStatus.Moved(now, newRef.organization, newRef.repository)
      for {
        _ <- db.insertRelease(Scalafix.artifact, Seq.empty, now)
        _ <- db.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
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
