package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.newModel.Artifact.ArtifactId
import ch.epfl.scala.index.newModel.Artifact.Name
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactIdTests extends AsyncFunSpec with Matchers {
  describe("parsing artifacts") {
    it("parses scalajs") {
      val artifactId = "cats-core_sjs0.6_2.11"
      val expected = ArtifactId(Name("cats-core"), Platform.ScalaJs(ScalaVersion.`2.11`, Platform.ScalaJs.`0.6`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId

    }

    it("parses scala-native") {
      val artifactId = "cats-core_native0.4_2.11"
      val expected = ArtifactId(Name("cats-core"), Platform.ScalaNative(ScalaVersion.`2.11`, MinorBinary(0, 4)))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parses scala3 versions") {
      val artifactId = "circe_cats-core_3.0.0-RC3"
      val expected = ArtifactId(
        Name("circe_cats-core"),
        Platform.ScalaJvm(Scala3Version(PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(3))))
      )
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parses scala3 compiler") {
      val artifactId = "scala3-compiler_3.0.0-RC1"
      val expected = ArtifactId(
        Name("scala3-compiler"),
        Platform.ScalaJvm(
          Scala3Version(PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(1)))
        )
      )
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parses sbt") {
      val artifactId = "sbt-microsites_2.12_1.0"
      val expected =
        ArtifactId(Name("sbt-microsites"), Platform.SbtPlugin(ScalaVersion.`2.12`, Platform.SbtPlugin.`1.0`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parse Java Artifact") {
      val artifactId = "sparrow"
      val expected = ArtifactId(Name("sparrow"), Platform.Java)
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("should not parse full scala version") {
      ArtifactId.parse("scalafix-core_2.12.2") shouldBe None
    }

    it("should not parse correctly") {
      ArtifactId.parse("scalafix-core_2.10_0.12") shouldBe None
    }

    it("handles special case") {
      val artifactId = "banana_jvm_2.11"
      val expected = ArtifactId(Name("banana_jvm"), Platform.ScalaJvm(ScalaVersion.`2.11`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }
  }
}
