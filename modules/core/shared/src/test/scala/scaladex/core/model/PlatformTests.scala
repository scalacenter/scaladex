package scaladex.core

import scaladex.core.model.*

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PlatformTests extends AnyFunSpec with Matchers:
  it("should parse a Platform from its value") {
    Platform.parse("sjs1").get shouldBe ScalaJs.`1.x`
    Platform.parse("jvm").get shouldBe Jvm
    Platform.parse("native0.4").get shouldBe ScalaNative.`0.4`
    Platform.parse("sbt1.0").get shouldBe SbtPlugin.`1.x`
    Platform.parse("sbt1").get shouldBe SbtPlugin.`1.x`
    Platform.parse("sbt2.0.0-M2").get shouldBe SbtPlugin(
      Version.SemanticLike(2, Some(0), Some(0), preRelease = Some(Milestone(2)))
    )
    Platform.parse("mill0.10").get shouldBe MillPlugin.`0.10`
  }
end PlatformTests
