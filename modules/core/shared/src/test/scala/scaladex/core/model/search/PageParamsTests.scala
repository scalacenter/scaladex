package scaladex.core.model.search

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PageParamsTests extends AnyFunSpec with Matchers:
  describe("AllowedSizes") {
    it("contains the default size") {
      PageParams.AllowedSizes should contain(PageParams.DefaultSize)
    }
  }
end PageParamsTests
