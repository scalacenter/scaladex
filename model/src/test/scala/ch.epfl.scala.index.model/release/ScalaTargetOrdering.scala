package ch.epfl.scala.index.model
package release

import org.scalatest._

class ScalaTargetOrdering extends FunSpec {
  describe("Scala Targets") {
    it("has an ordering") {
      val s210 = SemanticVersion("2.10.6").get
      val s211 = SemanticVersion("2.11.11").get
      val s212 = SemanticVersion("2.12.2").get
      val js067 = SemanticVersion("0.6.7").get
      val js0618 = SemanticVersion("0.6.18").get
      val nat03 = SemanticVersion("0.3.0").get

      val obtained = List(
        ScalaJs(s211, js067),
        ScalaJs(s212, js0618),
        ScalaJs(s210, js067),
        ScalaJs(s211, js0618),
        ScalaJs(s210, js0618),
        ScalaNative(s211, nat03),
        ScalaJvm(s212),
        ScalaJvm(s211),
        ScalaJvm(s210)
      ).sorted(ScalaTarget.ordering)

      val expected = List(
        ScalaNative(s211, nat03),
        ScalaJs(s210, js067),
        ScalaJs(s210, js0618),
        ScalaJs(s211, js067),
        ScalaJs(s211, js0618),
        ScalaJs(s212, js0618),
        ScalaJvm(s210),
        ScalaJvm(s211),
        ScalaJvm(s212)
      )

      assert(obtained == expected)
    }
  }
}
