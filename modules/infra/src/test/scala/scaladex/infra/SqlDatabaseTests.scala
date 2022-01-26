package scaladex.infra

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.util.ScalaExtensions._

class SqlDatabaseTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {

  val executorService: ExecutorService = Executors.newFixedThreadPool(1)
  override implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(executorService)

  import scaladex.core.test.Values._

  it("insert artifact and its dependencies") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      project <- database.getProject(Cats.reference)
      artifacts <- database.getArtifacts(Cats.reference)
    } yield {
      project should not be empty
      artifacts should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`)
    }
  }

  it("should get all project statuses") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(PlayJsonExtra.artifact, Seq.empty, now)
      projectStatuses <- database.getAllProjectsStatuses()
    } yield projectStatuses.keys should contain theSameElementsAs Seq(PlayJsonExtra.reference, Cats.reference)
  }

  it("should update project settings") {
    for {
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, ok)
      _ <- database.updateProjectSettings(Scalafix.reference, Scalafix.settings)
      scalafix <- database.getProject(Scalafix.reference)
    } yield scalafix.get shouldBe Scalafix.project
  }

  it("should update artifacts") {
    val newRef = Project.Reference.from("kindlevel", "dogs")
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(Cats.core_sjs1_3, Seq.empty, now)
      _ <- database.updataArtifacts(Seq(Cats.`core_3:2.6.1`, Cats.core_sjs1_3), newRef)
      oldArtifacts <- database.getArtifacts(Cats.reference)
      newArtifacts <- database.getArtifacts(newRef)
    } yield {
      oldArtifacts shouldBe empty
      newArtifacts should contain theSameElementsAs Seq(
        Cats.`core_3:2.6.1`.copy(projectRef = newRef),
        Cats.core_sjs1_3.copy(projectRef = newRef)
      )
    }
  }

  it("should update github status") {
    val failed = GithubStatus.Failed(now, 405, "Unauthorized")
    for {
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, ok)
      _ <- database.updateGithubStatus(Scalafix.reference, failed)
      scalafix <- database.getProject(Scalafix.reference)
    } yield scalafix.get.githubStatus shouldBe failed
  }

  it("should find artifacts by name") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(Cats.core_sjs1_3, Seq.empty, now)
      artifacts <- database.getArtifactsByName(Cats.reference, Cats.`core_3:2.6.1`.artifactName)
    } yield artifacts should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`, Cats.core_sjs1_3)
  }

  it("should count projects, artifacts, dependencies, github infos and data forms") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(Cats.core_sjs1_3, Seq.empty, now)
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now)
      _ <- database.insertArtifact(PlayJsonExtra.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
      _ <- database.updateProjectSettings(Scalafix.reference, Scalafix.settings)
      projects <- database.countProjects()
      artifacts <- database.countArtifacts()
      dependencies <- database.countDependencies()
      githubInfos <- database.countGithubInfo()
      settings <- database.countProjectSettings()
    } yield {
      projects shouldBe 3L
      artifacts shouldBe 4L
      dependencies shouldBe 3L
      githubInfos shouldBe 1L
      settings shouldBe 1L
    }
  }

  it("should find directDependencies") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(Cats.kernel_3, Seq.empty, now)
      directDependencies <- database.getDirectDependencies(Cats.`core_3:2.6.1`)
    } yield directDependencies.map(_.target) should contain theSameElementsAs List(Some(Cats.kernel_3), None, None)
  }

  it("should find reverseDependencies") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(Cats.kernel_3, Seq.empty, now)
      reverseDependencies <- database.getReverseDependencies(Cats.kernel_3)
    } yield reverseDependencies.map(_.source) should contain theSameElementsAs List(Cats.`core_3:2.6.1`)
  }

  it("should compute project dependencies") {
    val artifacts: Map[Artifact, Seq[ArtifactDependency]] = Map(
      Cats.`core_3:2.6.1` -> Seq(
        ArtifactDependency(
          source = Cats.`core_3:2.6.1`.mavenReference,
          target = Artifact.MavenReference("fake", "fake_3", "version"),
          scope = "compile"
        )
      ), // first case: a dependency to an external artifact
      Cats.kernel_3 -> Seq(
        ArtifactDependency(
          source = Cats.kernel_3.mavenReference,
          target = Cats.`core_3:2.6.1`.mavenReference,
          "compile"
        )
      ), // depends on it self
      Scalafix.artifact -> Cats.dependencies.map(
        _.copy(source = Scalafix.artifact.mavenReference)
      ), // dependends on two cats artifacts
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
      _ <- artifacts.map {
        case (artifact, dependencies) => database.insertArtifact(artifact, dependencies, now)
      }.sequence
      projectDependencies <- database.computeProjectDependencies()
      _ <- database.insertProjectDependencies(projectDependencies)
      catsInverseDependencies <- database.countInverseProjectDependencies(Cats.reference)
    } yield {
      projectDependencies shouldBe Seq(
        ProjectDependency(Scalafix.reference, Cats.reference),
        ProjectDependency(Cats.reference, Cats.reference),
        ProjectDependency(PlayJsonExtra.reference, Scalafix.reference)
      )
      catsInverseDependencies shouldBe 2
    }
  }
  it("should update creation date") {
    for {
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now)
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Seq.empty, now)
      _ <- database.insertArtifact(PlayJsonExtra.artifact, Seq.empty, now)
      creationDates <- database.computeAllProjectsCreationDates()
      _ <- creationDates.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
      projects <- database.getAllProjects()
    } yield {
      val creationDates = projects.map(p => p.reference -> p.creationDate)
      val expected = Seq(
        Cats.reference -> Cats.`core_3:2.6.1`.releaseDate,
        Scalafix.reference -> Scalafix.artifact.releaseDate,
        PlayJsonExtra.reference -> PlayJsonExtra.artifact.releaseDate
      )
      creationDates should contain theSameElementsAs expected
    }
  }
  it("should createMovedProject") {
    val destination = Project.Reference.from("scala", "fix")
    val moved = GithubStatus.Moved(now, destination)
    for {
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
      _ <- database.moveProject(Scalafix.reference, Scalafix.githubInfo, moved)
      newProject <- database.getProject(destination)
      oldProject <- database.getProject(Scalafix.reference)
    } yield {
      oldProject.get.githubStatus shouldBe moved
      newProject.get.reference shouldBe destination
      newProject.get.githubStatus shouldBe GithubStatus.Ok(now)
    }
  }

  it("should delete moved projects from project-dependency-table") {
    for {
      _ <- database.insertProjectDependencies(Seq(ProjectDependency(Scalafix.reference, Cats.reference)))
      _ <- database.insertProjectDependencies(Seq(ProjectDependency(PlayJsonExtra.reference, Scalafix.reference)))
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now)
      _ <- database.moveProject(
        Scalafix.reference,
        Scalafix.githubInfo,
        GithubStatus.Moved(now, Project.Reference.from("scala", "fix"))
      )
      _ <- database.deleteDependenciesOfMovedProject()
      scalafixInverseDeps <- database.countInverseProjectDependencies(Scalafix.reference)
      catsInverseDeps <- database.countInverseProjectDependencies(Cats.reference)
    } yield {
      scalafixInverseDeps shouldBe 0
      catsInverseDeps shouldBe 0
    }
  }
}
