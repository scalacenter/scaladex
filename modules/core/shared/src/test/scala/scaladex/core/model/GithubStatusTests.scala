package scaladex.core.model

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class GithubStatusTests extends AnyFunSpec with Matchers {
  val date: Instant = Instant.ofEpochMilli(1475505237265L)
  val now: Instant = Instant.now()
  describe("githubStatus") {
    it("should order correctly") {
      val unknown = GithubStatus.Unknown(now)
      val ok = GithubStatus.Ok(date)
      Seq(ok, unknown).sorted shouldBe Seq(unknown, ok)
    }
  }
}
