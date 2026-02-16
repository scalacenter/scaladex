package scaladex.data
package cleanup

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ScmInfoParserTests extends AnyFunSpec with Matchers:
  describe("ScmInfoParse") {
    it("correctly parse valid SCM strings") {
      ScmInfoParser
        .parseRawConnection("scm:git:git@github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parseRawConnection("scm:https://github.com/foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parseRawConnection("scm:https://github.com/foobarbuz/example")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parseRawConnection("scm:git:git://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parseRawConnection("scm:git://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parseRawConnection("scm:git:ssh://git@github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parseRawConnection("scm:git:ssh://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parseRawConnection("scm:git:unknown://git@github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe None
      ScmInfoParser
        .parseRawConnection("scm:git:unknown://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe None
      ScmInfoParser
        .parseRawConnection("scm:git@github.com:mghmay/play-json-shaper.git")
        .map(_.toString) shouldBe Some("mghmay/play-json-shaper")
    }
  }
end ScmInfoParserTests
