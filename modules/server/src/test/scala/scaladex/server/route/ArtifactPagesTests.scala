package scaladex.server.route

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.test.Values.Cats._

class ArtifactPagesTests extends AnyFunSpec with Matchers {
  describe("getDefault") {
    it("should prefer JVM") {
      ArtifactPages.getDefault(Seq(kernel_3, core_sjs1_3)) shouldBe kernel_3
    }
    it("should prefer higher Scala version") {
      ArtifactPages.getDefault(Seq(`core_2.13:2.6.1`, kernel_3)) shouldBe kernel_3
    }
    it("should use alphabetical order") {
      ArtifactPages.getDefault(Seq(`core_3:2.6.1`, kernel_3)) shouldBe `core_3:2.6.1`
    }
  }

}
