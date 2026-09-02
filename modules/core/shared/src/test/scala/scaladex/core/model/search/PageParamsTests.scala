package scaladex.core.model.search

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PageParamsTests extends AnyFunSpec with Matchers:
  describe("bounded") {
    it("forces page to at least 1") {
      PageParams.bounded(0, 20) shouldBe PageParams(1, 20)
      PageParams.bounded(-3, 20) shouldBe PageParams(1, 20)
    }

    it("clamps size to [1, MaxSize]") {
      PageParams.bounded(1, 0) shouldBe PageParams(1, 1)
      PageParams.bounded(1, -5) shouldBe PageParams(1, 1)
      PageParams.bounded(1, PageParams.MaxSize + 1000) shouldBe PageParams(1, PageParams.MaxSize)
    }

    it("leaves valid values untouched") {
      PageParams.bounded(3, 25) shouldBe PageParams(3, 25)
    }
  }
end PageParamsTests
