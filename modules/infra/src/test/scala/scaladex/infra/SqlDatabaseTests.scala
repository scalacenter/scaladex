package scaladex.infra

import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.model.GithubStatus
import scaladex.core.model.Jvm
import scaladex.core.model.Project
import scaladex.core.model.Project.Organization
import scaladex.core.model.ProjectDependency
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.util.ScalaExtensions._
import scaladex.core.util.Secret

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
      _ <- database.insertArtifact(Cats.`core_sjs1_3:2.6.1`, Seq.empty, now)
      _ <- database.updateArtifacts(Seq(Cats.`core_3:2.6.1`, Cats.`core_sjs1_3:2.6.1`), newRef)
      oldArtifacts <- database.getArtifacts(Cats.reference)
      newArtifacts <- database.getArtifacts(newRef)
    } yield {
      oldArtifacts shouldBe empty
      newArtifacts should contain theSameElementsAs Seq(
        Cats.`core_3:2.6.1`.copy(projectRef = newRef),
        Cats.`core_sjs1_3:2.6.1`.copy(projectRef = newRef)
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
      _ <- database.insertArtifact(Cats.`core_sjs1_3:2.6.1`, Seq.empty, now)
      artifacts <- database.getArtifactsByName(Cats.reference, Cats.`core_3:2.6.1`.artifactName)
    } yield artifacts should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`, Cats.`core_sjs1_3:2.6.1`)
  }

  it("should count projects, artifacts, dependencies, github infos and data forms") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(Cats.`core_sjs1_3:2.6.1`, Seq.empty, now)
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
      _ <- database.insertArtifact(Cats.`kernel_3:2.6.1`, Seq.empty, now)
      directDependencies <- database.getDirectDependencies(Cats.`core_3:2.6.1`)
    } yield {
      val targets = directDependencies.map(_.target)
      targets should contain theSameElementsAs List(Some(Cats.`kernel_3:2.6.1`), None, None)
    }
  }

  it("should find reverseDependencies") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertArtifact(Cats.`kernel_3:2.6.1`, Seq.empty, now)
      reverseDependencies <- database.getReverseDependencies(Cats.`kernel_3:2.6.1`)
    } yield reverseDependencies.map(_.source) should contain theSameElementsAs List(Cats.`core_3:2.6.1`)
  }

  it("should compute project dependencies") {
    for {
      _ <- database.insertArtifact(Cats.`core_2.13:2.6.1`, Seq.empty, now)
      _ <- database.insertArtifact(
        Cats.`kernel_2.13`,
        Seq(
          ArtifactDependency(Cats.`kernel_2.13`.mavenReference, Cats.`core_2.13:2.6.1`.mavenReference, Scope("compile"))
        ),
        now
      )
      _ <- database.insertArtifact(
        Scalafix.artifact,
        Seq(
          ArtifactDependency(Scalafix.artifact.mavenReference, Cats.`core_2.13:2.6.1`.mavenReference, Scope("compile"))
        ),
        now
      )
      scalafixDependencies <- database.computeProjectDependencies(Scalafix.reference, Scalafix.artifact.version)
      catsDependencies <- database.computeProjectDependencies(Cats.reference, Cats.`core_2.13:2.6.1`.version)
      _ <- database.insertProjectDependencies(scalafixDependencies ++ catsDependencies)
      catsDependents <- database.countProjectDependents(Cats.reference)
    } yield {
      scalafixDependencies shouldBe Seq(
        ProjectDependency(
          Scalafix.reference,
          Scalafix.artifact.version,
          Cats.reference,
          Cats.`core_2.13:2.6.1`.version,
          Scope("compile")
        )
      )
      catsDependencies shouldBe empty
      catsDependents shouldBe 1
      succeed
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
      val creationDates = projects.map(p => p.reference -> p.creationDate.get)
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

  it("should delete project dependencies") {
    val dependencies = Seq(
      ProjectDependency(
        Scalafix.reference,
        Scalafix.artifact.version,
        Cats.reference,
        Cats.`core_2.13:2.6.1`.version,
        Scope("compile")
      )
    )
    for {
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now)
      _ <- database.insertProjectDependencies(dependencies)
      _ <- database.deleteProjectDependencies(Scalafix.reference)
      catsDependents <- database.countProjectDependents(Cats.reference)
    } yield catsDependents shouldBe 0
  }

  it("should insert, update and get user session from id") {
    val userId = UUID.randomUUID()
    val userInfo = UserInfo("login", Some("name"), "", Secret("token"))
    val userState = UserState(Set(Scalafix.reference), Set(Organization("scalacenter")), userInfo)
    for {
      _ <- database.insertUser(userId, userInfo)
      state1 <- database.getUser(userId)
      _ = state1 shouldBe Some(UserState(Set.empty, Set.empty, userInfo))
      _ <- database.updateUser(userId, userState)
      obtained <- database.getUser(userId)
    } yield obtained shouldBe Some(userState)
  }

  it("should find all user sessions") {
    val firstUserId = UUID.randomUUID()
    val secondUserId = UUID.randomUUID()
    val firstUserInfo = UserInfo("first login", Some("first name"), "", Secret("firstToken"))
    val secondUserInfo = UserInfo("second login", Some("second name"), "", Secret("secondToken"))
    for {
      _ <- database.insertUser(firstUserId, firstUserInfo)
      _ <- database.insertUser(secondUserId, secondUserInfo)
      allUsers <- database.getAllUsers()
    } yield allUsers should contain theSameElementsAs Seq(firstUserId -> firstUserInfo, secondUserId -> secondUserInfo)
  }

  it("should delete by user id") {
    val userId = UUID.randomUUID()
    val userInfo = UserInfo("login", Some("name"), "", new Secret("token"))
    for {
      _ <- database.insertUser(userId, userInfo)
      _ <- database.deleteUser(userId)
      obtained <- database.getUser(userId)
    } yield obtained shouldBe None
  }

  it("should return artifact from maven reference") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`, Seq.empty, now)
      catsCore <- database.getArtifactByMavenReference(Cats.`core_3:2.6.1`.mavenReference)
    } yield catsCore should contain(Cats.`core_3:2.6.1`)
  }

  it("should return artifact from maven reference if version is only major") {
    for {
      _ <- database.insertArtifact(Cats.`core_3:4`, Seq.empty, now)
      catsCore <- database.getArtifactByMavenReference(Cats.`core_3:4`.mavenReference)
    } yield catsCore should contain(Cats.`core_3:4`)
  }

  it("should return all artifacts given no language or platform") {
    val testArtifacts = Seq(Scalafix.artifact, Cats.`core_3:4`, PlayJsonExtra.artifact)
    for {
      _ <- database.insertArtifacts(testArtifacts)
      storedArtifacts <- database.getAllArtifacts(None, None)
    } yield storedArtifacts.forall(testArtifacts.contains) shouldBe true
  }

  it("should return no artifacts given a language that has no artifacts stored") {
    for {
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now) // Scalafix has Scala version 2.13
      storedArtifacts <- database.getAllArtifacts(Some(Scala.`3`), None)
    } yield storedArtifacts.size shouldBe 0
  }

  it("should return no artifacts given a platform that has no artifacts stored") {
    for {
      _ <- database.insertArtifact(Scalafix.artifact, Seq.empty, now) // Scalafix is a JVM-platform artifact
      storedArtifacts <- database.getAllArtifacts(None, Some(ScalaJs.`1.x`))
    } yield storedArtifacts.size shouldBe 0
  }

  it("should return artifacts that conform to the given language and platform") {
    val testArtifacts = Seq(
      Cats.`core_3:4`,
      Scalafix.artifact.copy(version = SemanticVersion(3))
    )
    for {
      _ <- database.insertArtifacts(testArtifacts)
      storedArtifacts <- database.getAllArtifacts(Some(Scala.`3`), Some(Jvm))
    } yield storedArtifacts.forall(testArtifacts.contains) shouldBe true
  }

  it(
    "should not return artifacts given both language and platform, and the database contains no artifacts that conform"
  ) {
    val testArtifacts = Seq(
      Cats.`core_3:4`,
      Scalafix.artifact.copy(version = SemanticVersion(3))
    )
    for {
      _ <- database.insertArtifacts(testArtifacts)
      storedArtifacts <- database.getAllArtifacts(Some(Scala.`3`), Some(ScalaJs(SemanticVersion(3))))
    } yield storedArtifacts.size shouldBe 0
  }

  it("should not return any artifacts when the database is empty, given a group id and artifact id") {
    val testArtifact = Cats.`core_3:4`
    val testArtifactId = Artifact.ArtifactId
      .parse(testArtifact.artifactId)
      .getOrElse(fail("Parsing an artifact id should not have failed"))
    for {
      retrievedArtifacts <- database.getArtifacts(testArtifact.groupId, testArtifactId)
    } yield retrievedArtifacts.size shouldBe 0
  }

  it("should return an artifact, given a group id an artifact id of a stored artifact") {
    val testArtifact = Cats.`core_3:4`
    val testArtifactId = Artifact.ArtifactId
      .parse(testArtifact.artifactId)
      .getOrElse(fail("Parsing an artifact id should not have failed"))
    for {
      isStoredSuccessfully <- database.insertArtifact(testArtifact, dependencies = Cats.dependencies, now)
      retrievedArtifacts <- database.getArtifacts(testArtifact.groupId, testArtifactId)
    } yield {
      isStoredSuccessfully shouldBe true
      retrievedArtifacts.size shouldBe 1
      retrievedArtifacts.headOption shouldBe Some(testArtifact)
    }
  }

  it("should return all versions of an artifact given a group id and an artifact id") {
    val testArtifacts = Seq(Cats.`core_3:4`, Cats.`core_3:2.7.0`)
    val groupId = Artifact.GroupId("org.typelevel")
    val artifactId = Artifact.ArtifactId
      .parse("cats-core_3")
      .getOrElse(fail("Parsing an artifact id should not have failed"))
    for {
      _ <- database.insertArtifacts(testArtifacts)
      retrievedArtifacts <- database.getArtifacts(groupId, artifactId)
    } yield {
      retrievedArtifacts.size shouldBe 2
      retrievedArtifacts should contain theSameElementsAs testArtifacts
    }
  }
}
