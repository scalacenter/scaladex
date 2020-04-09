package ch.epfl.scala.index.model
package release

import org.scalatest._
import org.scalatest.prop.TableDrivenPropertyChecks

class ScalaTargetTests
    extends FunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  it("should be ordered") {
    val s210 = PatchBinary(2, 10, 6)
    val s211 = PatchBinary(2, 11, 11)
    val s212 = PatchBinary(2, 12, 2)
    val js067 = PatchBinary(0, 6, 7)
    val js0618 = PatchBinary(0, 6, 18)
    val nat03 = PatchBinary(0, 3, 0)

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
      ScalaJs(s211, js067),
      ScalaJs(s210, js0618),
      ScalaJs(s211, js0618),
      ScalaJs(s212, js0618),
      ScalaJvm(s210),
      ScalaJvm(s211),
      ScalaJvm(s212)
    )

    assert(obtained == expected)
  }

  it("should parse any scala target") {
    val cases = Table(
      ("input", "target"),
      ("_2.12", ScalaJvm(MinorBinary(2, 12))),
      ("_sjs0.6_2.12", ScalaJs(MinorBinary(2, 12), MinorBinary(0, 6)))
    )

    forAll(cases) { (input, target) =>
      ScalaTarget.parse(input) should contain(target)
    }
  }
}
