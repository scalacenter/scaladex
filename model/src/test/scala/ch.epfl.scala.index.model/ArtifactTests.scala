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
            ScalaTarget.scalaJs(
              scalaVersion = SemanticVersion("2.11").get,
              scalaJsVersion = SemanticVersion("0.6").get
            )
          )
        )
      )
    }

    it("parses scala-native") {
      assert(
        Artifact("cats-core_native0.1_2.11") == Some(
          (
            "cats-core",
            ScalaTarget.scalaNative(
              scalaVersion = SemanticVersion("2.11").get,
              scalaNativeVersion = SemanticVersion("0.1").get
            )
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
              scalaVersion = SemanticVersion("2.11.0-RC4").get
            )
          )
        )
      )
    }

    it("parses sbt") {
      assert(
        Artifact("sbt-microsites_2.12_1.0") == Some(
          (
            "sbt-microsites",
            ScalaTarget.sbt(
              scalaVersion = SemanticVersion("2.12").get,
              sbtVersion = SemanticVersion("1.0").get
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
            ScalaTarget.scala(
              scalaVersion = SemanticVersion("2.11").get
            )
          )
        )
      )
    }
  }
}
