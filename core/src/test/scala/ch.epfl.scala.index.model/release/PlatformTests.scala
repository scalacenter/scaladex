package ch.epfl.scala.index.model
package release

import org.scalatest.OptionValues
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class PlatformTests
    extends AsyncFunSpec
    with Matchers
    with OptionValues
    with TableDrivenPropertyChecks {
  it("should be ordered") {
    val js067 = PatchBinary(0, 6, 7)
    val js0618 = PatchBinary(0, 6, 18)
    val nat03 = PatchBinary(0, 3, 0)

    val obtained = List(
      Platform.ScalaJs(ScalaVersion.`2.10`, js067),
      Platform.ScalaJs(ScalaVersion.`2.12`, js0618),
      Platform.ScalaJs(ScalaVersion.`2.11`, js067),
      Platform.ScalaJs(ScalaVersion.`2.11`, js0618),
      Platform.ScalaJs(ScalaVersion.`2.10`, js0618),
      Platform.ScalaNative(ScalaVersion.`2.11`, nat03),
      Platform.ScalaJvm(ScalaVersion.`2.12`),
      Platform.ScalaJvm(ScalaVersion.`2.11`),
      Platform.ScalaJvm(ScalaVersion.`2.10`)
    ).sorted(Platform.ordering)

    val expected = List(
      Platform.ScalaNative(ScalaVersion.`2.11`, nat03),
      Platform.ScalaJs(ScalaVersion.`2.10`, js067),
      Platform.ScalaJs(ScalaVersion.`2.11`, js067),
      Platform.ScalaJs(ScalaVersion.`2.10`, js0618),
      Platform.ScalaJs(ScalaVersion.`2.11`, js0618),
      Platform.ScalaJs(ScalaVersion.`2.12`, js0618),
      Platform.ScalaJvm(ScalaVersion.`2.10`),
      Platform.ScalaJvm(ScalaVersion.`2.11`),
      Platform.ScalaJvm(ScalaVersion.`2.12`)
    )

    assert(obtained == expected)
  }

  it("should parse any scala target") {
    val cases = Table(
      ("input", "target"),
      ("_2.12", Platform.ScalaJvm(ScalaVersion.`2.12`)),
      ("_3", Platform.ScalaJvm(Scala3Version.`3`)),
      ("_sjs0.6_2.12", Platform.ScalaJs(ScalaVersion.`2.12`, MinorBinary(0, 6)))
    )

    forAll(cases) { (input, target) =>
      Platform.parse(input) should contain(target)
    }
  }

  it("should parse a string to yield a ScalaTargetType") {
    ScalaTargetType.ofName("Js").value shouldBe ScalaTargetType.Js
    ScalaTargetType.ofName("Jvm").value shouldBe ScalaTargetType.Jvm
  }
  it("Should encode and parse a ScalaTarget") {
    val st = Platform.ScalaJs(ScalaVersion.`2.10`, MinorBinary(0, 6))
    println(s"st.encode = ${st.encode}")
    assert(Platform.parse(st.encode).get == st)
  }
}
