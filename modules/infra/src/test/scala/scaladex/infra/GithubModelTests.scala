package scaladex.infra

import java.time.Instant

import scaladex.infra.github.GithubModel

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class GithubModelTests extends AnyFunSpec with Matchers:
  describe("GithubModel") {
    it("should parse creationDate") {
      val time = "2015-01-28T20:26:48Z"
      GithubModel.parseToInstant(time) shouldBe Some(Instant.parse("2015-01-28T20:26:48Z"))
    }
  }
