package scaladex.infra

import java.util.UUID
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import scaladex.core.model.*
import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.util.ScalaExtensions.*
import scaladex.core.util.Secret

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class SqlDatabaseTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers:
  given ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))
  override def executionContext: ExecutionContext = ec

  import scaladex.core.test.Values.*

  it("insert artifact") {
    for
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`)
      artifacts <- database.getArtifact(Cats.`core_3:2.6.1`.reference)
    yield artifacts should contain(Cats.`core_3:2.6.1`)
  }

  it("get all project statuses") {
    for
      _ <- database.insertProjectRef(Cats.reference, unknown)
      _ <- database.insertProjectRef(PlayJsonExtra.reference, unknown)
      projectStatuses <- database.getAllProjectsStatuses()
    yield projectStatuses.keys should contain theSameElementsAs Seq(PlayJsonExtra.reference, Cats.reference)
  }

  it("update project settings") {
    for
      _ <- database.insertProjectRef(Scalafix.reference, unknown)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, ok)
      _ <- database.updateProjectSettings(Scalafix.reference, Scalafix.settings)
      scalafix <- database.getProject(Scalafix.reference)
    yield scalafix.get shouldBe Scalafix.project
  }

  it("update artifacts") {
    val newRef = Project.Reference.from("kindlevel", "dogs")
    for
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`)
      _ <- database.insertArtifact(Cats.`core_sjs1_3:2.6.1`)
      _ <- database.updateArtifacts(Seq(Cats.`core_3:2.6.1`.reference, Cats.`core_sjs1_3:2.6.1`.reference), newRef)
      obtained1 <- database.getProjectArtifactRefs(Cats.reference, stableOnly = false)
      obtained2 <- database.getProjectArtifactRefs(newRef, stableOnly = false)
    yield
      obtained1 shouldBe empty
      obtained2 should contain theSameElementsAs Seq(
        Cats.`core_3:2.6.1`.reference,
        Cats.`core_sjs1_3:2.6.1`.reference
      )
    end for
  }

  it("update github status") {
    val failed = GithubStatus.Failed(now, 405, "Unauthorized")
    for
      _ <- database.insertProjectRef(Scalafix.reference, unknown)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, ok)
      _ <- database.updateGithubStatus(Scalafix.reference, failed)
      scalafix <- database.getProject(Scalafix.reference)
    yield scalafix.get.githubStatus shouldBe failed
  }

  it("get artifacts by project and name") {
    val artifacts = Seq(Cats.`core_3:2.6.1`, Cats.`core_sjs1_3:2.6.1`, Cats.`kernel_2.13:2.6.1`)
    for
      _ <- database.insertArtifacts(artifacts)
      obtained1 <- database.getProjectArtifactRefs(Cats.reference, stableOnly = false)
      obtained2 <- database.getProjectArtifactRefs(Cats.reference, Artifact.Name("cats-core"))
    yield
      obtained1 should contain theSameElementsAs artifacts.map(_.reference)
      obtained2 should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`, Cats.`core_sjs1_3:2.6.1`).map(_.reference)
  }

  it("get artifacts by project and version") {
    val artifacts = Seq(Cats.`core_3:2.6.1`, Cats.`core_sjs1_3:2.6.1`, Cats.`kernel_2.13:2.6.1`, Cats.`core_2.13:2.5.0`)
    for
      _ <- database.insertArtifacts(artifacts)
      obtained1 <- database.getProjectArtifactRefs(Cats.reference, `2.6.1`)
      obtained2 <- database.getProjectArtifactRefs(Cats.reference, `2.5.0`)
    yield
      obtained1 should contain theSameElementsAs Seq(
        Cats.`core_3:2.6.1`,
        Cats.`core_sjs1_3:2.6.1`,
        Cats.`kernel_2.13:2.6.1`
      ).map(_.reference)
      obtained2 should contain theSameElementsAs Seq(Cats.`core_2.13:2.5.0`).map(_.reference)
    end for
  }

  it("should count projects, artifacts, dependencies, github infos and data forms") {
    for
      _ <- database.insertProjectRef(Cats.reference, unknown)
      _ <- database.insertArtifacts(Seq(Cats.`core_3:2.6.1`, Cats.`core_sjs1_3:2.6.1`))
      _ <- database.insertDependencies(Cats.dependencies)
      _ <- database.insertProjectRef(Scalafix.reference, unknown)
      _ <- database.insertArtifact(Scalafix.artifact)
      _ <- database.insertProjectRef(PlayJsonExtra.reference, unknown)
      _ <- database.insertArtifact(PlayJsonExtra.artifact)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
      _ <- database.updateProjectSettings(Scalafix.reference, Scalafix.settings)
      projects <- database.countProjects()
      artifacts <- database.countArtifacts()
      dependencies <- database.countDependencies()
      githubInfos <- database.countGithubInfo()
      settings <- database.countProjectSettings()
    yield
      projects shouldBe 3L
      artifacts shouldBe 4L
      dependencies shouldBe 3L
      githubInfos shouldBe 1L
      settings shouldBe 1L
  }

  it("should find directDependencies") {
    for
      _ <- database.insertArtifacts(Seq(Cats.`core_3:2.6.1`, Cats.`kernel_3:2.6.1`))
      _ <- database.insertDependencies(Cats.dependencies)
      directDependencies <- database.getDirectDependencies(Cats.`core_3:2.6.1`)
    yield
      val targets = directDependencies.map(_.target)
      targets should contain theSameElementsAs List(Some(Cats.`kernel_3:2.6.1`), None, None)
  }

  it("should find reverseDependencies") {
    for
      _ <- database.insertArtifacts(Seq(Cats.`core_3:2.6.1`, Cats.`kernel_3:2.6.1`))
      _ <- database.insertDependencies(Cats.dependencies)
      reverseDependencies <- database.getReverseDependencies(Cats.`kernel_3:2.6.1`, limit = 100, offset = 0)
    yield
      val sources = reverseDependencies.map(_.source)
      sources should contain theSameElementsAs List(Cats.`core_3:2.6.1`)
  }

  it("should dedup reverse dependencies and count the deduped ones") {
    val deps = Seq(
      ArtifactDependency(Cats.`core_3:2.6.1`.reference, Cats.`laws_3:2.6.1`.reference, Scope("compile")),
      ArtifactDependency(Cats.`core_3:4`.reference, Cats.`laws_3:2.6.1`.reference, Scope("compile"))
    )
    for
      _ <- database.insertArtifacts(Seq(Cats.`core_3:2.6.1`, Cats.`core_3:4`, Cats.`laws_3:2.6.1`))
      _ <- database.insertDependencies(deps)
      reverseDependencies <- database.getReverseDependencies(Cats.`laws_3:2.6.1`, limit = 100, offset = 0)
      count <- database.countReverseDependencies(Cats.`laws_3:2.6.1`)
    yield
      reverseDependencies should have size 1
      count shouldBe 1L
  }

  it("should compute project dependencies") {
    for
      _ <- database.insertProjectRef(Cats.reference, unknown)
      _ <- database.insertArtifacts(Seq(Cats.`core_2.13:2.6.1`, Cats.`kernel_2.13:2.6.1`))
      _ <- database.insertDependencies(
        Seq(ArtifactDependency(Cats.`kernel_2.13:2.6.1`.reference, Cats.`core_2.13:2.6.1`.reference, Scope("compile")))
      )
      _ <- database.insertProjectRef(Scalafix.reference, unknown)
      _ <- database.insertArtifact(Scalafix.artifact)
      _ <- database.insertDependencies(
        Seq(ArtifactDependency(Scalafix.artifact.reference, Cats.`core_2.13:2.6.1`.reference, Scope("compile")))
      )
      scalafixDependencies <- database.computeProjectDependencies(Scalafix.reference, Scalafix.artifact.version)
      catsDependencies <- database.computeProjectDependencies(Cats.reference, Cats.`core_2.13:2.6.1`.version)
      _ <- database.insertProjectDependencies(scalafixDependencies ++ catsDependencies)
      catsDependents <- database.countProjectDependents(Cats.reference)
      catsReverseDependencies <- database.getProjectReverseDependencies(Cats.reference, limit = 100, offset = 0)
    yield
      val expectedDependency = ProjectDependency(
        Scalafix.reference,
        Scalafix.artifact.version,
        Cats.reference,
        Cats.`core_2.13:2.6.1`.version,
        Scope("compile")
      )
      scalafixDependencies shouldBe Seq(expectedDependency)
      catsDependencies shouldBe empty
      catsDependents shouldBe 1
      catsReverseDependencies shouldBe Seq(expectedDependency)
  }
  it("should update creation date") {
    for
      _ <- database.insertProjectRef(Scalafix.reference, unknown)
      _ <- database.insertArtifact(Scalafix.artifact)
      _ <- database.insertProjectRef(Cats.reference, unknown)
      _ <- database.insertArtifact(Cats.`core_3:2.6.1`)
      _ <- database.insertProjectRef(PlayJsonExtra.reference, unknown)
      _ <- database.insertArtifact(PlayJsonExtra.artifact)
      creationDates <- database.computeProjectsCreationDates()
      _ <- creationDates.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
      projects <- database.getAllProjects()
    yield
      val creationDates = projects.map(p => p.reference -> p.creationDate.get)
      val expected = Seq(
        Cats.reference -> Cats.`core_3:2.6.1`.releaseDate,
        Scalafix.reference -> Scalafix.artifact.releaseDate,
        PlayJsonExtra.reference -> PlayJsonExtra.artifact.releaseDate
      )
      creationDates should contain theSameElementsAs expected
  }
  it("should create moved project") {
    val destination = Project.Reference.from("scala", "fix")
    val moved = GithubStatus.Moved(now, destination)
    for
      _ <- database.insertProjectRef(Scalafix.reference, unknown)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
      _ <- database.moveProject(Scalafix.reference, Scalafix.githubInfo, moved)
      newProject <- database.getProject(destination)
      oldProject <- database.getProject(Scalafix.reference)
    yield
      oldProject.get.githubStatus shouldBe moved
      newProject.get.reference shouldBe destination
      newProject.get.githubStatus shouldBe GithubStatus.Ok(now)
    end for
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
    for
      _ <- database.insertProjectRef(Scalafix.reference, unknown)
      _ <- database.insertProjectDependencies(dependencies)
      _ <- database.deleteProjectDependencies(Scalafix.reference)
      catsDependents <- database.countProjectDependents(Cats.reference)
    yield catsDependents shouldBe 0
  }

  it("should insert, update and get user session from id") {
    val userId = UUID.randomUUID()
    val userInfo = UserInfo("login", Some("name"), "", Secret("token"))
    val userState = UserState(Set(Scalafix.reference), Set(Project.Organization("scalacenter")), userInfo)
    for
      _ <- database.insertUser(userId, userInfo)
      state1 <- database.getUser(userId)
      _ = state1 shouldBe Some(UserState(Set.empty, Set.empty, userInfo))
      _ <- database.updateUser(userId, userState)
      obtained <- database.getUser(userId)
    yield obtained shouldBe Some(userState)
  }

  it("should find all user sessions") {
    val firstUserId = UUID.randomUUID()
    val secondUserId = UUID.randomUUID()
    val firstUserInfo = UserInfo("first login", Some("first name"), "", Secret("firstToken"))
    val secondUserInfo = UserInfo("second login", Some("second name"), "", Secret("secondToken"))
    for
      _ <- database.insertUser(firstUserId, firstUserInfo)
      _ <- database.insertUser(secondUserId, secondUserInfo)
      allUsers <- database.getAllUsers()
    yield allUsers should contain theSameElementsAs Seq(firstUserId -> firstUserInfo, secondUserId -> secondUserInfo)
  }

  it("should delete by user id") {
    val userId = UUID.randomUUID()
    val userInfo = UserInfo("login", Some("name"), "", new Secret("token"))
    for
      _ <- database.insertUser(userId, userInfo)
      _ <- database.deleteUser(userId)
      obtained <- database.getUser(userId)
    yield obtained shouldBe None
  }

  it("get artifact by ref") {
    for
      _ <- database.insertArtifacts(Seq(Cats.`core_3:2.6.1`, Cats.`core_3:4`))
      obtained1 <- database.getArtifact(Cats.`core_3:2.6.1`.reference)
      obtained2 <- database.getArtifact(Cats.`core_3:4`.reference)
    yield
      obtained1 should contain(Cats.`core_3:2.6.1`)
      obtained2 should contain(Cats.`core_3:4`)
  }

  it("get all artifacts by language and platform") {
    val artifacts = Seq(Scalafix.artifact, Cats.`core_sjs1_3:2.6.1`, PlayJsonExtra.artifact)
    for
      _ <- database.insertArtifacts(artifacts)
      obtained1 <- database.getAllArtifacts(None, None)
      obtained2 <- database.getAllArtifacts(Some(Scala.`3`), None)
      obtained3 <- database.getAllArtifacts(None, Some(ScalaJs.`1.x`))
      obtained4 <- database.getAllArtifacts(Some(Scala.`2.13`), Some(Jvm))
      obtained5 <- database.getAllArtifacts(Some(Scala.`3`), Some(ScalaJs.`0.6`))
    yield
      obtained1 should contain theSameElementsAs artifacts
      obtained2 should contain theSameElementsAs Seq(Cats.`core_sjs1_3:2.6.1`)
      obtained3 should contain theSameElementsAs Seq(Cats.`core_sjs1_3:2.6.1`)
      obtained4 should contain theSameElementsAs Seq(Scalafix.artifact)
      obtained5 shouldBe empty
    end for
  }

  it("getArtifact by groupId and artifactId") {
    val artifacts = Seq(Cats.`core_2.13:2.5.0`, Cats.`core_3:2.6.1`)
    for
      _ <- database.insertArtifacts(artifacts)
      obtained1 <- database.getArtifacts(Cats.reference, Cats.groupId, Artifact.ArtifactId("cats-core_3"))
      obtained2 <- database.getArtifacts(Cats.reference, Cats.groupId, Artifact.ArtifactId("cats-core_2.13"))
    yield
      obtained1 should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`)
      obtained2 should contain theSameElementsAs Seq(Cats.`core_2.13:2.5.0`)
  }

  it("insert, list by status and review discovered group ids") {
    val now = java.time.Instant.now
    val newGroup = DiscoveredGroupId.pending(DiscoveredGroupId.Source.MavenIndex, Artifact.GroupId("dev.new"), now)
    val rejected = DiscoveredGroupId
      .pending(DiscoveredGroupId.Source.Manual, Artifact.GroupId("dev.spam"), now)
      .copy(status = DiscoveredGroupId.Status.Rejected)
    for
      inserted <- database.insertDiscoveredGroupIds(Seq(newGroup, rejected))
      // re-inserting must not overwrite the rejected status
      reinserted <- database.insertDiscoveredGroupIds(
        Seq(DiscoveredGroupId.pending(DiscoveredGroupId.Source.MavenIndex, Artifact.GroupId("dev.spam"), now))
      )
      _ <- database.updateDiscoveredGroupIdSync(
        Artifact.GroupId("dev.new"),
        now,
        "Inserted 3 poms",
        Seq(Cats.reference)
      )
      pending <- database.getDiscoveredGroupIds(DiscoveredGroupId.Status.Pending)
      _ <- database.updateDiscoveredGroupIdStatus(
        Artifact.GroupId("dev.new"),
        DiscoveredGroupId.Status.Reviewed,
        "admin",
        now
      )
      afterReview <- database.getDiscoveredGroupIds(DiscoveredGroupId.Status.Pending)
      all <- database.getAllDiscoveredGroupIds()
    yield
      inserted shouldBe 2
      reinserted shouldBe 0
      pending.map(_.groupId.value) shouldBe Seq("dev.new")
      pending.head.syncSummary shouldBe Some("Inserted 3 poms")
      pending.head.projectRefs shouldBe Seq(Cats.reference)
      afterReview shouldBe empty
      all.map(_.groupId.value).sorted shouldBe Seq("dev.new", "dev.spam")
    end for
  }

  it("sync queue is FIFO and an error keeps a group retryable") {
    val t0 = java.time.Instant.parse("2026-01-01T00:00:00Z")
    val older = DiscoveredGroupId.pending(DiscoveredGroupId.Source.MavenIndex, Artifact.GroupId("dev.older"), t0)
    val newer = DiscoveredGroupId.pending(
      DiscoveredGroupId.Source.MavenIndex,
      Artifact.GroupId("dev.newer"),
      t0.plusSeconds(3600)
    )
    for
      _ <- database.insertDiscoveredGroupIds(Seq(newer, older))
      toSync <- database.getPendingDiscoveredGroupIdsToSync(10)
      _ <- database.updateDiscoveredGroupIdError(Artifact.GroupId("dev.older"), "error: boom")
      stillQueued <- database.getPendingDiscoveredGroupIdsToSync(10)
      _ <- database.updateDiscoveredGroupIdSync(Artifact.GroupId("dev.newer"), t0, "Inserted 0 poms", Nil)
      afterOneSynced <- database.getPendingDiscoveredGroupIdsToSync(10)
    yield
      toSync.map(_.groupId.value) shouldBe Seq("dev.older", "dev.newer") // oldest first
      stillQueued.map(_.groupId.value) shouldBe Seq("dev.older", "dev.newer") // error did not set last_synced_at
      afterOneSynced.map(_.groupId.value) shouldBe Seq("dev.older") // dev.newer left the queue
    end for
  }

  it("stores and reads back the maven index cursor") {
    for
      empty <- database.getMavenIndexCursor()
      _ <- database.setMavenIndexCursor(IndexCursor("chain-1", 900))
      first <- database.getMavenIndexCursor()
      _ <- database.setMavenIndexCursor(IndexCursor("chain-1", 912))
      second <- database.getMavenIndexCursor()
    yield
      empty shouldBe None
      first shouldBe Some(IndexCursor("chain-1", 900))
      second shouldBe Some(IndexCursor("chain-1", 912))
  }
end SqlDatabaseTests
