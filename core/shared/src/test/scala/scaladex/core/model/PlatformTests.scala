package scaladex.core.model

import org.scalatest.OptionValues
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scaladex.core.model.Platform._

class PlatformTests extends AsyncFunSpec with Matchers with OptionValues with TableDrivenPropertyChecks {
  it("should be ordered") {
    val js067 = PatchBinary(0, 6, 7)
    val js0618 = PatchBinary(0, 6, 18)
    val nat03 = PatchBinary(0, 3, 0)

    val obtained = List(
      ScalaJs(ScalaVersion.`2.10`, js067),
      ScalaJs(ScalaVersion.`2.12`, js0618),
      ScalaJs(ScalaVersion.`2.11`, js067),
      ScalaJs(ScalaVersion.`2.11`, js0618),
      ScalaJs(ScalaVersion.`2.10`, js0618),
      ScalaNative(ScalaVersion.`2.11`, nat03),
      ScalaJvm(ScalaVersion.`2.12`),
      ScalaJvm(ScalaVersion.`2.11`),
      ScalaJvm(ScalaVersion.`2.10`)
    ).sorted(ordering)

    val expected = List(
      ScalaNative(ScalaVersion.`2.11`, nat03),
      ScalaJs(ScalaVersion.`2.10`, js067),
      ScalaJs(ScalaVersion.`2.11`, js067),
      ScalaJs(ScalaVersion.`2.10`, js0618),
      ScalaJs(ScalaVersion.`2.11`, js0618),
      ScalaJs(ScalaVersion.`2.12`, js0618),
      ScalaJvm(ScalaVersion.`2.10`),
      ScalaJvm(ScalaVersion.`2.11`),
      ScalaJvm(ScalaVersion.`2.12`)
    )

    assert(obtained == expected)
  }

  it("should parse any scala target") {
    val cases = Table(
      ("input", "target"),
      ("_2.12", ScalaJvm(ScalaVersion.`2.12`)),
      ("_3", ScalaJvm(Scala3Version.`3`)),
      ("_sjs0.6_2.12", ScalaJs(ScalaVersion.`2.12`, MinorBinary(0, 6)))
    )

    forAll(cases)((input, target) => parse(input) should contain(target))
  }

  it("should parse a string to yield a ScalaTargetType") {
    PlatformType.ofName("Js").value shouldBe PlatformType.Js
    PlatformType.ofName("Jvm").value shouldBe PlatformType.Jvm
  }
  it("Should encode and parse a ScalaTarget") {
    val st = ScalaJs(ScalaVersion.`2.10`, MinorBinary(0, 6))
    println(s"st.encode = ${st.encode}")
    assert(parse(st.encode).get == st)
  }
}
