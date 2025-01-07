package scaladex.data

import scala.util.Success
import scala.util.Try

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class IndexConfigTests extends AnyFunSpec with Matchers {
  describe("AppConf") {
    it("should load the conf") {
      Try(IndexConfig.load()) shouldBe a[Success[_]]
    }
  }
}
