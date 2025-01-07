package scaladex.infra

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.github.GithubModel

class GithubModelTests extends AnyFunSpec with Matchers:
  describe("GithubModel") {
    it("should parse creationDate") {
      val time = "2015-01-28T20:26:48Z"
      GithubModel.parseToInstant(time) shouldBe Some(Instant.parse("2015-01-28T20:26:48Z"))
    }
  }
