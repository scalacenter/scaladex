package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.release._
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactTests extends AsyncFunSpec with Matchers {
  describe("parsing artifacts") {
    it("parses scalajs") {
      Artifact.parse("cats-core_sjs0.6_2.11") should contain(
        Artifact(
          "cats-core",
          Platform.ScalaJs(ScalaVersion.`2.11`, Platform.ScalaJs.`0.6`)
        )
      )
    }

    it("parses scala-native") {
      Artifact.parse("cats-core_native0.4_2.11") should contain(
        Artifact(
          "cats-core",
          Platform.ScalaNative(ScalaVersion.`2.11`, MinorBinary(0, 4))
        )
      )
    }

    it("parses scala3 versions") {
      Artifact.parse("circe_cats-core_3.0.0-RC3") should contain(
        Artifact(
          "circe_cats-core",
          Platform.ScalaJvm(
            Scala3Version(PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(3)))
          )
        )
      )
    }

    it("parses scala3 compiler") {
      Artifact.parse("scala3-compiler_3.0.0-RC1") should contain(
        Artifact(
          "scala3-compiler",
          Platform.ScalaJvm(
            Scala3Version(PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(1)))
          )
        )
      )
    }

    it("parses sbt") {
      Artifact.parse("sbt-microsites_2.12_1.0") should contain(
        Artifact(
          "sbt-microsites",
          Platform.SbtPlugin(ScalaVersion.`2.12`, Platform.SbtPlugin.`1.0`)
        )
      )
    }

    it("parse Java Artifact") {
      Artifact.parse("sparrow") shouldBe Some(
        Artifact("sparrow", Platform.Java)
      )
    }

    it("should not parse full scala version") {
      Artifact.parse("scalafix-core_2.12.2") shouldBe None
    }

    it("handles special case") {
      Artifact.parse("banana_jvm_2.11") should contain(
        Artifact(
          "banana_jvm",
          Platform.ScalaJvm(ScalaVersion.`2.11`)
        )
      )
    }
  }
}
