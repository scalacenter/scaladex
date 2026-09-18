package scaladex.infra

import java.time.Instant

import scaladex.infra.github.GithubModel

import io.circe.parser.decode
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class GithubModelTests extends AnyFunSpec with Matchers:
  describe("GithubModel") {
    it("should parse creationDate") {
      val time = "2015-01-28T20:26:48Z"
      GithubModel.parseToInstant(time) shouldBe Some(Instant.parse("2015-01-28T20:26:48Z"))
    }

    it("should parse a community profile with null files") {
      val json = """{"files":{"contributing":null,"code_of_conduct":null,"license":null}}"""
      decode[GithubModel.CommunityProfile](json) shouldBe Right(GithubModel.CommunityProfile(None, None, None))
    }

    it("should parse a community profile with present files") {
      val json =
        """{"files":{
          |"contributing":{"html_url":"https://github.com/foo/bar/blob/main/CONTRIBUTING.md"},
          |"code_of_conduct":{"html_url":"https://github.com/foo/bar/blob/main/CODE_OF_CONDUCT.md"},
          |"license":{"html_url":"https://github.com/foo/bar/blob/main/LICENSE"}
          |}}""".stripMargin
      decode[GithubModel.CommunityProfile](json) shouldBe Right(
        GithubModel.CommunityProfile(
          Some("https://github.com/foo/bar/blob/main/CONTRIBUTING.md"),
          Some("https://github.com/foo/bar/blob/main/CODE_OF_CONDUCT.md"),
          Some("https://github.com/foo/bar/blob/main/LICENSE")
        )
      )
    }
  }
end GithubModelTests
