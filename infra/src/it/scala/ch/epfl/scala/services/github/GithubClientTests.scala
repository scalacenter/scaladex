package ch.epfl.scala.services.github

import akka.actor.ActorSystem
import ch.epfl.scala.index.newModel.Project
import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class GithubClientTests extends AsyncFunSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem("github-downloader")
  val githubConfig: Option[GithubConfig] = GithubConfig.from(ConfigFactory.load())

  // you need to configure locally a token
  val github = new GithubClient(githubConfig.get.token)
  val scalafixRepo: Project.Reference = Project.Reference.from("scala", "scala")

  describe("githubClient") {
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
    it("fetchOrgaRepo") {
      for {
        repos <- github.fetchReposUnderUserOrganizations("atry", Nil)
      } yield assert(true)
    }
    it("fetchUserRepo") {
      for {
        repos <- github.fetchUserRepo("atry", Nil)
      } yield assert(true)
    }
    it("fetchUser") {
      for {
        userInfo <- github.fetchUser()
      } yield assert(true)
    }
    it("fetchUserOrganizations") {
      for {
        orgs <- github.fetchUserOrganizations("atry")
      } yield assert(true)
    }
  }
}
