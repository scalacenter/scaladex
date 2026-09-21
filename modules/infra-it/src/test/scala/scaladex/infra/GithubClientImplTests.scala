package scaladex.infra

import scala.concurrent.Await
import scala.concurrent.duration.*

import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubResponse.*
import scaladex.core.model.Project
import scaladex.core.model.UserState
import scaladex.core.test.Values.*
import scaladex.core.util.Secret
import scaladex.infra.config.GithubConfig

import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class GithubClientImplTests extends AsyncFunSpec with Matchers with Eventually with ScalaFutures:
  given ActorSystem = ActorSystem("github-client-tests")
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 30.seconds, interval = 2.seconds)
  val config: GithubConfig = GithubConfig.load()
  val isCI: Boolean = System.getenv("CI") != null
  val token: Secret = config.token.getOrElse(throw new Exception(s"Missing GITHUB_TOKEN"))
  val client = GithubClientImpl(config.scheduledHttpClient)
  val userStateOpt: Option[UserState] =
    if isCI then None
    else
      Await.result(client.getUserState(token), 30.seconds) match
        case Failed(code, errorMessage) => throw new Exception(s"$code $errorMessage")
        case NotFound(code) => throw new Exception(s"not found: $code")
        case MovedPermanently(userState) => Some(userState)
        case Ok(userState) => Some(userState)

  it("getProjectInfo") {
    for response <- client.getProjectInfo(Scalafix.reference, token)
    yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }
  it("getRepository") {
    for response <- client.getRepository(Scalafix.reference, token)
    yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }
  it("getRepository with no license") {
    for response <- client.getRepository(Project.Reference.unsafe("mainstreethub/sbt-parent-plugin"), token)
    yield response match
      case GithubResponse.Ok(repo) => repo.licenseName shouldBe empty
      case _ => fail()
  }
  it("getReadme") {
    for readme <- client.getReadme(Scalafix.reference, token)
    yield readme shouldBe defined
  }
  it("getCommunity") {
    for communityProfile <- client.getCommunityProfile(Cats.reference, token)
    yield
      communityProfile.licenceFile shouldBe defined
      communityProfile.codeOfConductFile shouldBe defined
      communityProfile.contributingFile shouldBe defined
  }
  it("getContributors") {
    for contributors <- client.getContributors(Cats.reference, token)
    yield
      contributors should not be empty
      contributors.head.html_url should startWith("https://github.com/")
  }
  it("getOpenIssues") {
    for openIssues <- client.getOpenIssues(Scalafix.reference, token)
    yield openIssues should not be empty
  }

  it("should return moved project") {
    val reference = Project.Reference.from("rickynils", "scalacheck")
    for response <- client.getProjectInfo(reference, token)
    yield response should matchPattern { case GithubResponse.MovedPermanently(_) => () }
  }

  it("should return empty contributor list") {
    val reference = Project.Reference.from("intive", "domofon")
    for contributors <- client.getContributors(reference, token)
    yield contributors shouldBe empty
  }

  it("should return empty issue list") {
    val reference = Project.Reference.from("scala", "scala")
    for issues <- client.getOpenIssues(reference, token)
    yield issues shouldBe empty
  }

  it("getUserInfo") {
    for response <- client.getUserInfo(token)
    yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }

  it("getPercentageOfLanguage should return 0, given a repo with none of the target language") {
    for percentOfLanguage <- client.getPercentageOfLanguage(Scalafix.reference, "Racket", token)
    yield percentOfLanguage shouldBe 0
  }

  it("should return a non-zero value, given a repo which contains the target language") {
    for percentOfLanguage <- client.getPercentageOfLanguage(Scalafix.reference, "Scala", token)
    yield percentOfLanguage > 0 shouldBe true
  }

  it("getCommitActivity") {
    eventually {
      client.getCommitActivity(Scala3.reference, token).futureValue should not be empty
    }
  }

  it("should return empty commit activity list") {
    val reference = Project.Reference.from("intive", "domofon")
    for commitActivities <- client.getCommitActivity(reference, token)
    yield commitActivities shouldBe empty
  }

  userStateOpt.foreach { userState =>
    it("getUserRepositories") {
      for repos <- client.getUserRepositories("atry", Nil, token)
      yield repos should not be empty
    }
    it("getUserOrganizations when empty") {
      for orgs <- client.getUserOrganizations("central-ossrh", token)
      yield orgs shouldBe empty
    }
    it("getUserOrganizations") {
      for orgs <- client.getUserOrganizations("atry", token)
      yield orgs should not be empty
    }

    if userState.orgs.contains(Scala3.organization) then
      it("getOrganizationRepositories") {
        for repos <- client.getOrganizationRepositories(userState.info.login, Scala3.organization, Nil, token)
        yield repos should contain(Scala3.reference)
      }
  }
end GithubClientImplTests
