package scaladex.core

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Jvm
import scaladex.core.model.MillPlugin
import scaladex.core.model.Platform
import scaladex.core.model.SbtPlugin
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative

class PlatformTests extends AnyFunSpec with Matchers {
  it("should yield a Platform from its label") {
    Platform.fromLabel("sjs1").get shouldBe ScalaJs.`1.x`
    Platform.fromLabel("jvm").get shouldBe Jvm
    Platform.fromLabel("native0.4").get shouldBe ScalaNative.`0.4`
    Platform.fromLabel("sbt1.0").get shouldBe SbtPlugin.`1.0`
    Platform.fromLabel("mill0.10").get shouldBe MillPlugin.`0.10`
  }
}
