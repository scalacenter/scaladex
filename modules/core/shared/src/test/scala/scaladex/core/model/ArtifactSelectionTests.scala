package scaladex.core.model

import scaladex.core.model.Artifact.*

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers   

class ArtifactSelectionTests extends AnyFunSpec with Matchers:

  describe("ArtifactSelection") {

    it("selects the artifact matching the repository name ignoring case") {

      val projectRef = Project.Reference.from("ScalaFX", "ScalaFX")
      val project = Project.default(projectRef)

      val oldArtifact =
        Artifact.Reference(
          GroupId("org.scalafx"),
          ArtifactId("ScalaFX"),
          Version("8.0.0")
        )

      val currentArtifact =
        Artifact.Reference(
          GroupId("org.scalafx"),
          ArtifactId("scalafx"),
          Version("14.0.0")
        )

      val result =
        ArtifactSelection(None, None)
          .filterArtifacts(Seq(oldArtifact, currentArtifact), project)

      result.head.name.value shouldBe "scalafx"
      result.head.version.toString shouldBe "14.0.0"
    }
  }

end ArtifactSelectionTests
