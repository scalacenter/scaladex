package scaladex.core.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact._

class ArtifactIdTests extends AsyncFunSpec with Matchers {
  describe("parsing artifacts") {
    it("parses scalajs") {
      val artifactId = "cats-core_sjs0.6_2.11"
      val expected = ArtifactId(Name("cats-core"), BinaryVersion(ScalaJs.`0.6`, Scala.`2.11`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId

    }

    it("parses scala-native") {
      val artifactId = "cats-core_native0.4_2.11"
      val expected = ArtifactId(Name("cats-core"), BinaryVersion(ScalaNative.`0.4`, Scala.`2.11`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parses scala3 versions") {
      val artifactId = "circe_cats-core_3"
      val expected = ArtifactId(Name("circe_cats-core"), BinaryVersion(Jvm, Scala.`3`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parses scala3 compiler") {
      val artifactId = "scala3-compiler_3"
      val expected = ArtifactId(Name("scala3-compiler"), BinaryVersion(Jvm, Scala.`3`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parses sbt") {
      val artifactId = "sbt-microsites_2.12_1.0"
      val expected =
        ArtifactId(Name("sbt-microsites"), BinaryVersion(SbtPlugin.`1.0`, Scala.`2.12`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }

    it("parse Java Artifact") {
      val artifactId = "sparrow"
      val expected = ArtifactId(Name("sparrow"), BinaryVersion(Jvm, Java))
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
      val expected = ArtifactId(Name("banana_jvm"), BinaryVersion(Jvm, Scala.`2.11`))
      val result = ArtifactId.parse(artifactId)
      result should contain(expected)
      result.get.value shouldBe artifactId
    }
  }
}
