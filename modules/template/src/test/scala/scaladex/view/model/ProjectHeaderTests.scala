package scaladex.view.model

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.test.Values._

class ProjectHeaderTests extends AnyFunSpec with Matchers {
  import Cats._

  describe("default artifact") {
    it("should prefer JVM") {
      getDefaultArtifact(`kernel_3:2.6.1`, `core_sjs1_3:2.6.1`) shouldBe `kernel_3:2.6.1`
    }
    it("should prefer higher Scala version") {
      getDefaultArtifact(`kernel_3:2.6.1`, `core_2.13:2.6.1`) shouldBe `kernel_3:2.6.1`
    }
    it("should use alphabetical order") {
      getDefaultArtifact(`core_3:2.6.1`, `kernel_3:2.6.1`) shouldBe `core_3:2.6.1`
    }
  }

  private def getDefaultArtifact(artifacts: Artifact*): Artifact = {
    val header = ProjectHeader(reference, artifacts, 10, None, false).get
    header.getDefaultArtifact(None, None)
  }
}
