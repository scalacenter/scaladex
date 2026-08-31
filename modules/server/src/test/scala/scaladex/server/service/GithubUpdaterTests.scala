package scaladex.server.service

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration.*

import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.service.GithubClient
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.Values.*
import scaladex.infra.GithubException

import org.apache.pekko.pattern.CircuitBreakerOpenException
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

/** A GithubClient whose getProjectInfo is scripted; other methods are unused by GithubUpdater. */
class StubGithubClient(
    getProjectInfoFn: Project.Reference => Future[GithubResponse[(Project.Reference, GithubInfo)]]
) extends GithubClient:
  override def getProjectInfo(ref: Project.Reference): Future[GithubResponse[(Project.Reference, GithubInfo)]] =
    getProjectInfoFn(ref)
  override def getUserInfo(): Future[GithubResponse[UserInfo]] = ???
  override def getUserState(): Future[GithubResponse[UserState]] = ???
  override def getUserOrganizations(login: String): Future[Seq[Project.Organization]] = ???
  override def getUserRepositories(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]] = ???
  override def getOrganizationRepositories(
      user: String,
      organization: Project.Organization,
      filterPermissions: Seq[String]
  ): Future[Seq[Project.Reference]] = ???
end StubGithubClient

class GithubUpdaterTests extends AsyncFunSpec with Matchers:
  private def ref(repo: String): Project.Reference = Project.Reference.from("org", repo)

  it("does not overwrite existing github info when the update fails") {
    val db = new InMemoryDatabase()
    val projectRef = ref("repo1")
    val goodInfo = GithubInfo.empty.copy(stars = Some(42))
    val updater = new GithubUpdater(db, new StubGithubClient(_ => Future.failed(GithubException(500, "boom"))))(using
      executionContext
    )
    for
      _ <- db.insertProjectRef(projectRef, unknown)
      _ <- db.updateGithubInfoAndStatus(projectRef, goodInfo, ok)
      _ <- updater.updateAll()
      project <- db.getProject(projectRef)
    yield
      project.flatMap(_.githubInfo) shouldBe Some(goodInfo)
      project.map(_.githubStatus) should matchPattern { case Some(_: GithubStatus.Failed) => () }
  }

  it("continues updating remaining projects when a single project fails") {
    val db = new InMemoryDatabase()
    val okRef1 = ref("ok1")
    val failRef = ref("fail")
    val okRef2 = ref("ok2")
    val info = GithubInfo.empty.copy(stars = Some(7))
    def stub(r: Project.Reference): Future[GithubResponse[(Project.Reference, GithubInfo)]] =
      if r == failRef then Future.failed(GithubException(500, "boom"))
      else Future.successful(GithubResponse.Ok(r -> info))
    val updater = new GithubUpdater(db, new StubGithubClient(stub))(using executionContext)
    for
      _ <- db.insertProjectRef(okRef1, unknown)
      _ <- db.insertProjectRef(failRef, unknown)
      _ <- db.insertProjectRef(okRef2, unknown)
      summary <- updater.updateAll()
      p1 <- db.getProject(okRef1)
      p2 <- db.getProject(failRef)
      p3 <- db.getProject(okRef2)
    yield
      p1.flatMap(_.githubInfo) shouldBe Some(info)
      p3.flatMap(_.githubInfo) shouldBe Some(info)
      p2.map(_.githubStatus) should matchPattern { case Some(_: GithubStatus.Failed) => () }
      summary should include("Failed")
  }

  it("stops the run early when the github circuit breaker is open") {
    val db = new InMemoryDatabase()
    val refs = (1 to 3).map(i => ref(s"repo$i"))
    val calls = new AtomicInteger(0)
    val info = GithubInfo.empty.copy(stars = Some(1))
    def stub(r: Project.Reference): Future[GithubResponse[(Project.Reference, GithubInfo)]] =
      if calls.incrementAndGet() == 1 then Future.successful(GithubResponse.Ok(r -> info))
      else Future.failed(new CircuitBreakerOpenException(0.seconds))
    val updater = new GithubUpdater(db, new StubGithubClient(stub))(using executionContext)
    for
      _ <- Future.traverse(refs)(db.insertProjectRef(_, unknown))
      summary <- updater.updateAll()
      statuses <- db.getAllProjectsStatuses()
    yield
      calls.get() shouldBe 2 // one success, one breaker-open, then stop before the third
      statuses.values.count(_.isInstanceOf[GithubStatus.Ok]) shouldBe 1
      summary should include("stopped early")
  }
end GithubUpdaterTests
