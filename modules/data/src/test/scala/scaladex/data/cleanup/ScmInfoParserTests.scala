package scaladex.data
package cleanup

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ScmInfoParserTests extends AnyFunSpec with Matchers:
  describe("ScmInfoParse") {
    it("correctly parse valid SCM strings") {
      // Implicit protocol
      ScmInfoParser
        .parse("scm:git:git@github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      // HTTPS
      ScmInfoParser
        .parse("scm:https://github.com/foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parse("scm:https://github.com/foobarbuz/example")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      // Git
      ScmInfoParser
        .parse("scm:git:git://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parse("scm:git://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      // SSH
      ScmInfoParser
        .parse("scm:git:ssh://git@github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      ScmInfoParser
        .parse("scm:git:ssh://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe Some("foobarbuz/example")
      // Unknown protocol
      ScmInfoParser
        .parse("scm:git:unknown://git@github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe None
      ScmInfoParser
        .parse("scm:git:unknown://github.com:foobarbuz/example.git")
        .map(_.toString) shouldBe None
    }
  }
end ScmInfoParserTests
