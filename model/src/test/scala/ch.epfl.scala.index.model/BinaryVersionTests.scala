package ch.epfl.scala.index.model

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSpec, Matchers}

class BinaryVersionTests
    extends FunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  it("should parse any binary version") {
    val inputs = Table(
      ("input", "output"),
      ("1", MajorBinary(1)),
      ("1.x", MajorBinary(1)),
      ("2.12", MinorBinary(2, 12)),
      ("0.6", MinorBinary(0, 6)),
      ("2.13.0", PatchBinary(2, 13, 0)),
      ("0.4.0", PatchBinary(0, 4, 0)),
      ("0.4.0-M2", PreReleaseBinary(0, 4, Some(0), Milestone(2))),
      ("0.23.0-RC1", PreReleaseBinary(0, 23, Some(0), ReleaseCandidate(1))),
      ("1.1-M1", PreReleaseBinary(1, 1, None, Milestone(1)))
    )

    forAll(inputs) { (input, output) =>
      BinaryVersion.parse(input) should contain(output)
    }
  }

  it("should be ordered") {
    val inputs = Table[BinaryVersion, BinaryVersion](
      ("lower", "higher"),
      (MajorBinary(1), MajorBinary(2)),
      (MajorBinary(1), MinorBinary(1, 1)), // 1.x < 1.1
      (MajorBinary(1), MinorBinary(2, 1)),
      (PreReleaseBinary(1, 2, None, Milestone(1)), MinorBinary(1, 2)),
      (MajorBinary(1), PreReleaseBinary(2, 0, None, Milestone(1)))
    )

    forAll(inputs) { (lower, higher) =>
      lower shouldBe <(higher)
    }
  }
}
