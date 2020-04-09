package ch.epfl.scala.index.model

import release._

import org.scalatest._

class ArtifactTests extends FunSpec with Matchers {
  describe("parsing artifacts") {
    it("parses scalajs") {
      Artifact.parse("cats-core_sjs0.6_2.11") should contain(
        Artifact(
          "cats-core",
          ScalaJs(
            scalaVersion = MinorBinary(2, 11),
            scalaJsVersion = MinorBinary(0, 6)
          )
        )
      )
    }

    it("parses scala-native") {
      Artifact.parse("cats-core_native0.1_2.11") should contain(
        Artifact(
          "cats-core",
          ScalaNative(
            scalaVersion = MinorBinary(2, 11),
            scalaNativeVersion = MinorBinary(0, 1)
          )
        )
      )
    }

    it("parses scala rc") {
      Artifact.parse("akka-remote-tests_2.11.0-RC4") should contain(
        Artifact(
          "akka-remote-tests",
          ScalaJvm(PreReleaseBinary(2, 11, Some(0), ReleaseCandidate(4)))
        )
      )
    }

    it("parses sbt") {
      Artifact.parse("sbt-microsites_2.12_1.0") should contain(
        Artifact(
          "sbt-microsites",
          SbtPlugin(
            scalaVersion = MinorBinary(2, 12),
            sbtVersion = MinorBinary(1, 0)
          )
        )
      )
    }

    it("does not parse unconventionnal") {
      Artifact.parse("sparrow") shouldBe empty
    }

    it("handles special case") {
      Artifact.parse("banana_jvm_2.11") should contain(
        Artifact(
          "banana_jvm",
          ScalaJvm(MinorBinary(2, 11))
        )
      )
    }
  }
}
