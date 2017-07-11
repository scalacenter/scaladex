package ch.epfl.scala.index.model

import release._

import org.scalatest._

class ArtifactTests extends FunSpec {
  describe("parsing artifacts") {
    it("parses scalajs") {
      assert(
        Artifact("cats-core_sjs0.6_2.11") == Some(
          (
            "cats-core",
            ScalaTarget.scalaJs(SemanticVersion(2, 11), SemanticVersion(0, 6))
          )
        )
      )
    }

    it("parses scala-native") {
      assert(
        Artifact("cats-core_native0.1_2.11") == Some(
          (
            "cats-core",
            ScalaTarget.scalaNative(SemanticVersion(2, 11),
                                    SemanticVersion(0, 1))
          )
        )
      )
    }

    it("parses scala rc") {
      assert(
        Artifact("akka-remote-tests_2.11.0-RC4") == Some(
          (
            "akka-remote-tests",
            ScalaTarget.scala(
              SemanticVersion(2,
                              11,
                              Some(0),
                              preRelease = Some(ReleaseCandidate(4)))
            )
          )
        )
      )
    }

    it("does not parse unconventionnal") {
      assert(Artifact("sparrow") == None)
    }

    it("handles special case") {
      assert(
        Artifact("banana_jvm_2.11") == Some(
          (
            "banana_jvm",
            ScalaTarget.scala(SemanticVersion(2, 11))
          )
        )
      )
    }
  }
}
