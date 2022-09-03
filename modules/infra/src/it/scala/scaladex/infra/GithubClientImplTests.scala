package scaladex.infra

import akka.actor.ActorSystem
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Project
import scaladex.infra.config.GithubConfig
import scaladex.core.model.GithubResponse
import scaladex.core.test.Values._

class GithubClientImplTests extends AsyncFunSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem("github-client-tests")
  val config: GithubConfig = GithubConfig.load()

  val isCI = System.getenv("CI") != null

  // you need to configure locally a token
  val client = new GithubClientImpl(config.token.get)

  it("getProjectInfo") {
    for (response <- client.getProjectInfo(Scalafix.reference))
      yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }
  it("getRepository") {
    for (response <- client.getRepository(Scalafix.reference))
      yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }
  it("getRepository with no license") {
    for (response <- client.getRepository(Project.Reference.from("mainstreethub/sbt-parent-plugin")))
      yield response match {
        case GithubResponse.Ok(repo) => repo.licenseName shouldBe empty
        case _                       => fail()
      }
  }
  it("getReadme") {
    for (readme <- client.getReadme(Scalafix.reference))
      yield readme shouldBe defined
  }
  it("getCommunity") {
    for (communityProfile <- client.getCommunityProfile(Cats.reference))
      yield {
        communityProfile shouldBe defined
        communityProfile.flatMap(_.licenceFile) shouldBe defined
        communityProfile.flatMap(_.codeOfConductFile) shouldBe defined
        communityProfile.flatMap(_.contributingFile) shouldBe defined
      }
  }
  it("getContributors") {
    for (contributors <- client.getContributors(Cats.reference))
      yield {
        contributors should not be empty
        contributors.head.html_url should startWith("https://github.com/")
      }
  }
  it("getOpenIssues") {
    for (openIssues <- client.getOpenIssues(Scalafix.reference))
      yield openIssues should not be empty
  }
  it("getGitterChatRoom") {
    for (chatroom <- client.getGitterChatRoom(Scalafix.reference))
      yield chatroom shouldBe defined
  }

  it("should return moved project") {
    val reference = Project.Reference.from("rickynils", "scalacheck")
    for (response <- client.getProjectInfo(reference))
      yield response should matchPattern { case GithubResponse.MovedPermanently(_) => () }
  }

  it("should return empty contributor list") {
    val reference = Project.Reference.from("intive", "domofon")
    for (contributors <- client.getContributors(reference))
      yield contributors shouldBe empty
  }

  it("should return empty issue list") {
    val reference = Project.Reference.from("scala", "scala")
    for (issues <- client.getOpenIssues(reference))
      yield issues shouldBe empty
  }

  it("getUserInfo") {
    for (response <- client.getUserInfo())
      yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }

  it("getPercentageOfLanguage should return 0, given a repo with none of the target language") {
    for (percentOfLanguage <- client.getPercentageOfLanguage(Scalafix.reference, "Racket"))
      yield percentOfLanguage shouldBe 0
  }

  it("should return a non-zero value, given a repo which contains the target language") {
    for (percentOfLanguage <- client.getPercentageOfLanguage(Scalafix.reference, "Scala"))
      yield percentOfLanguage > 0 shouldBe true
  }

  it("getCommitActivity") {
    for (commitActivities <- client.getCommitActivity(Scalafix.reference))
      yield commitActivities should not be empty
  }

  it("should return empty commit activity list") {
    val reference = Project.Reference.from("intive", "domofon")
    for (commitActivities <- client.getCommitActivity(reference))
      yield commitActivities shouldBe empty
  }

  if (!isCI) {
    it("getUserRepositories") {
      for (repos <- client.getUserRepositories("atry", Nil))
        yield repos should not be empty
    }
    it("getUserOrganizations when empty") {
      for (orgs <- client.getUserOrganizations("central-ossrh"))
        yield orgs shouldBe empty
    }
    it("getUserOrganizations") {
      for (orgs <- client.getUserOrganizations("atry"))
        yield orgs should not be empty
    }
    it("getOrganizationRepositories") {
      for (repos <- client.getOrganizationRepositories("atry", Project.Organization("scala"), Nil))
        yield repos should not be empty
    }
  }
}
