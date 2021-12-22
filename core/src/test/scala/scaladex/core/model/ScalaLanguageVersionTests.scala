package scaladex.core.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ScalaLanguageVersionTests extends AsyncFunSpec with Matchers {
  describe("Scala3Version") {
    it("should accept version 3") {
      assert(Scala3Version.isValid(MajorBinary(3)))
    }

    it("should render version 3") {
      assert(Scala3Version(MajorBinary(3)).render == "scala 3")
    }

    it("should not accept 3.x versions") {
      assert(!Scala3Version.isValid(MinorBinary(3, 0)))
    }

    it("should accept 3.x.y versions") {
      assert(!Scala3Version.isValid(PatchBinary(3, 0, 1)))
    }
  }

  describe("parseScalaTarget") {
    it("should not recognize scala3") {
      val res = ScalaLanguageVersion.tryParse("scala3")
      assert(res.isEmpty, res)
    }

    it("should recognize 3") {
      val res = ScalaLanguageVersion.tryParse("3")
      assert(res.nonEmpty, res)
    }

    it("should not recognize 3.0.1") {
      val res = ScalaLanguageVersion.tryParse("3.0.1")
      assert(res.isEmpty, res)
    }

    it("should recognize 3.0.1-RC1") {
      val res = ScalaLanguageVersion.tryParse("3.0.1-RC1")
      assert(res.isDefined, res)
    }

  }
}
