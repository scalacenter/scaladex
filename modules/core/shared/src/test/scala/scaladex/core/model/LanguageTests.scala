package scaladex.core.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class LanguageTests extends AsyncFunSpec with Matchers:
  describe("Scala 3 versions") {
    it("Scala 3 label") {
      Scala.`3`.label shouldBe "3.x"
      Scala.`3`.value shouldBe "3"
    }

    it("should not accept minor versions") {
      Scala(Version(3, 0)).isValid shouldBe false
    }

    it("should not accept patch versions") {
      Scala(Version("3.0.1")).isValid shouldBe false
    }
  }
end LanguageTests
