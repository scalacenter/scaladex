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
            languageVersion = ScalaVersion.`2.11`,
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
            languageVersion = ScalaVersion.`2.11`,
            scalaNativeVersion = MinorBinary(0, 1)
          )
        )
      )
    }

    it("parses dotty versions") {
      Artifact.parse("circe_0.23") should contain(
        Artifact(
          "circe",
          ScalaJvm(DottyVersion(MinorBinary(0, 23)))
        )
      )
    }

    it("parses sbt") {
      Artifact.parse("sbt-microsites_2.12_1.0") should contain(
        Artifact(
          "sbt-microsites",
          SbtPlugin(
            languageVersion = ScalaVersion.`2.12`,
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
          ScalaJvm(ScalaVersion.`2.11`)
        )
      )
    }
  }
}
