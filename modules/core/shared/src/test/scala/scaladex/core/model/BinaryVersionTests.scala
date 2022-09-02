package scaladex.core.model

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class BinaryVersionTests extends AnyFunSpec with Matchers with OptionValues with TableDrivenPropertyChecks {
  it("should be ordered") {
    val `0.6.7` = PatchVersion(0, 6, 7)
    val `0.6.18` = PatchVersion(0, 6, 18)
    val `0.3.0` = PatchVersion(0, 3, 0)

    val obtained = List(
      BinaryVersion(ScalaJs(`0.6.7`), Scala.`2.10`),
      BinaryVersion(ScalaJs(`0.6.18`), Scala.`2.12`),
      BinaryVersion(ScalaJs(`0.6.7`), Scala.`2.11`),
      BinaryVersion(ScalaJs(`0.6.18`), Scala.`2.11`),
      BinaryVersion(ScalaJs(`0.6.18`), Scala.`2.10`),
      BinaryVersion(ScalaNative(`0.3.0`), Scala.`2.11`),
      BinaryVersion(Jvm, Scala.`2.12`),
      BinaryVersion(Jvm, Scala.`2.11`),
      BinaryVersion(Jvm, Scala.`2.10`),
      BinaryVersion(MillPlugin.`0.10`, Scala.`2.13`)
    ).sorted

    val expected = List(
      BinaryVersion(MillPlugin.`0.10`, Scala.`2.13`),
      BinaryVersion(ScalaNative(`0.3.0`), Scala.`2.11`),
      BinaryVersion(ScalaJs(`0.6.7`), Scala.`2.10`),
      BinaryVersion(ScalaJs(`0.6.7`), Scala.`2.11`),
      BinaryVersion(ScalaJs(`0.6.18`), Scala.`2.10`),
      BinaryVersion(ScalaJs(`0.6.18`), Scala.`2.11`),
      BinaryVersion(ScalaJs(`0.6.18`), Scala.`2.12`),
      BinaryVersion(Jvm, Scala.`2.10`),
      BinaryVersion(Jvm, Scala.`2.11`),
      BinaryVersion(Jvm, Scala.`2.12`)
    )

    assert(obtained == expected)
  }

  it("should parse any binary version") {
    val cases = Table(
      ("input", "target"),
      ("_2.12", BinaryVersion(Jvm, Scala.`2.12`)),
      ("_3", BinaryVersion(Jvm, Scala.`3`)),
      ("_sjs0.6_2.12", BinaryVersion(ScalaJs.`0.6`, Scala.`2.12`)),
      ("_mill0.10_2.13", BinaryVersion(MillPlugin.`0.10`, Scala.`2.13`))
    )

    forAll(cases) { (input, expected) =>
      BinaryVersion.parse(input) should contain(expected)
      expected.encode shouldBe input
    }
  }
}
