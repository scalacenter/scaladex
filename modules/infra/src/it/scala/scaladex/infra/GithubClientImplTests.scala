package scaladex.infra

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.ActorSystem
import org.scalactic.source.Position
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.GithubResponse
import scaladex.core.model.GithubResponse._
import scaladex.core.model.Project
import scaladex.core.test.Values._
import scaladex.infra.config.GithubConfig

class GithubClientImplTests extends AsyncFunSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem("github-client-tests")
  val config: GithubConfig = GithubConfig.load()
  val isCI = System.getenv("CI") != null
  val token = config.token.getOrElse(throw new Exception(s"Missing GITHUB_TOKEN"))
  val client = new GithubClientImpl(token)
  val userStateOpt =
    if (isCI) None
    else
      Await.result(client.getUserState(), 30.seconds) match {
        case Failed(code, errorMessage)  => throw new Exception(s"$code $errorMessage")
        case MovedPermanently(userState) => Some(userState)
        case Ok(userState)               => Some(userState)
      }

  it("getProjectInfo") {
    for (response <- client.getProjectInfo(Scalafix.reference))
      yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }
  it("getRepository") {
    for (response <- client.getRepository(Scalafix.reference))
      yield response should matchPattern { case GithubResponse.Ok(_) => () }
  }
  it("getRepository with no license") {
    for (response <- client.getRepository(Project.Reference.unsafe("mainstreethub/sbt-parent-plugin")))
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
    for (commitActivities <- client.getCommitActivity(Scala3.reference))
      yield commitActivities should not be empty
  }

  it("should return empty commit activity list") {
    val reference = Project.Reference.from("intive", "domofon")
    for (commitActivities <- client.getCommitActivity(reference))
      yield commitActivities shouldBe empty
  }

  userStateOpt.foreach { userState =>
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

    if (userState.orgs.contains(Scala3.organization)) {
      it("getOrganizationRepositories") {
        for (repos <- client.getOrganizationRepositories(userState.info.login, Scala3.organization, Nil))
          yield repos should contain(Scala3.reference)
      }
    }
  }
}
