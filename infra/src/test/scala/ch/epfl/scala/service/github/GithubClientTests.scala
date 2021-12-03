package ch.epfl.scala.service.github

import akka.actor.ActorSystem
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.services.github.GithubClient
import ch.epfl.scala.services.github.GithubConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class GithubClientTests extends AsyncFunSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem("github-downloader")
  val githubConfig: Option[GithubConfig] = GithubConfig.from(ConfigFactory.load())

  // Configure TOKEN_FOR_GITHUB to be able to test github api
  val github = new GithubClient(githubConfig.get)
  val scalafixRepo: GithubRepo = GithubRepo("playframework", "playframework")

  describe("GihhubImplementation") {
    it("getReadme") {
      for {
        readme <- github.getReadme(scalafixRepo)
      } yield assert(readme.nonEmpty)
    }
    it("getCommunity") {
      for {
        communityProfile <- github.getCommunityProfile(scalafixRepo)
      } yield assert(true)
    }
    it("getRepository") {
      for {
        repo <- github.getRepoInfo(scalafixRepo)
      } yield assert(true)
    }
    it("getContributors") {
      for {
        contributors <- github.getContributors(scalafixRepo)
      } yield assert(true)
    }
    it("getOpenIssues") {
      for {
        openIssues <- github.getOpenIssues(scalafixRepo)
      } yield assert(true)
    }
    it("getGiterChatRoom") {
      for {
        chatroom <- github.getGiterChatRoom(scalafixRepo)
      } yield assert(true)
    }
  }
}
