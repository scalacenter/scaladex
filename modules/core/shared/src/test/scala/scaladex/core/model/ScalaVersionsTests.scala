package scaladex.core.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ScalaVersionTests extends AsyncFunSpec with Matchers {
  describe("Scala3Version") {
    it("should accept version 3") {
      assert(ScalaVersion.isValid(MajorBinary(3)))
    }

    it("should render version 3") {
      assert(ScalaVersion.`3`.render == "Scala 3")
    }

    it("should not accept 3.x versions") {
      assert(!ScalaVersion.isValid(MinorBinary(3, 0)))
    }

    it("should not accept 3.x.y versions") {
      assert(!ScalaVersion.isValid(PatchBinary(3, 0, 1)))
    }
  }

  describe("parse ScalaVersion") {
    it("should recognize 3") {
      val res = ScalaVersion.parse("3")
      assert(res.nonEmpty, res)
    }

    it("should not recognize 3.0.1") {
      val res = ScalaVersion.parse("3.0.1")
      assert(res.isEmpty, res)
    }

    it("should not recognize 3.0.1-RC1") {
      val res = ScalaVersion.parse("3.0.1-RC1")
      assert(res.isEmpty, res)
    }
  }
}
