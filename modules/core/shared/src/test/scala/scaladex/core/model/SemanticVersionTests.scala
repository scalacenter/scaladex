package scaladex.core.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scaladex.core.model.SemanticVersion.PreferStable
import scaladex.core.test.Values._

class SemanticVersionTests extends AsyncFunSpec with Matchers with TableDrivenPropertyChecks {
  it("should parse any version") {
    val inputs = Table(
      ("input", "output"),
      ("1", MajorVersion(1)),
      ("2.12", MinorVersion(2, 12)),
      ("0.6", MinorVersion(0, 6)),
      ("2.13.0", PatchVersion(2, 13, 0)),
      ("0.4.0", PatchVersion(0, 4, 0)),
      ("0.4.0-M2", PreReleaseVersion(0, 4, 0, Milestone(2))),
      ("0.23.0-RC1", PreReleaseVersion(0, 23, 0, ReleaseCandidate(1))),
      ("3.0.0-M1", PreReleaseVersion(3, 0, 0, Milestone(1))),
      ("1.1-M1", SemanticVersion(1, Some(1), preRelease = Some(Milestone(1)))),
      ("1.2.3.4", SemanticVersion(1, Some(2), Some(3), Some(4))),
      ("1.1.1-xyz", SemanticVersion(1, Some(1), Some(1), preRelease = Some(OtherPreRelease("xyz")))),
      ("1.1.1+some.meta~data", SemanticVersion(1, Some(1), Some(1), metadata = Some("some.meta~data")))
    )

    forAll(inputs)((input, output) => SemanticVersion.parse(input) should contain(output))
  }

  it("should be ordered") {
    val inputs = Table[SemanticVersion, SemanticVersion](
      ("lower", "higher"),
      (MajorVersion(1), MajorVersion(2)),
      (MinorVersion(1, 1), MajorVersion(1)),
      (MajorVersion(1), MinorVersion(2, 1)),
      (SemanticVersion(1, Some(2), preRelease = Some(Milestone(1))), MinorVersion(1, 2)),
      (PreReleaseVersion(1, 2, 0, Milestone(1)), MinorVersion(1, 2)),
      (MajorVersion(1), SemanticVersion(2, Some(0), preRelease = Some(Milestone(1))))
    )

    forAll(inputs)((lower, higher) => lower shouldBe <(higher))
  }

  it("should allow us to prefer releases over pre-releases") {
    val versions = Seq(`7.0.0`, `7.1.0`, `7.2.0-PREVIEW.1`)
    versions.max shouldBe `7.2.0-PREVIEW.1`
    versions.max(PreferStable) shouldBe `7.1.0`
  }

  it("should encode and decode any version") {
    val inputs = Table[SemanticVersion](
      "semanticVersion",
      MajorVersion(2345),
      MinorVersion(1, 3),
      SemanticVersion(1, Some(2), Some(3), Some(4))
    )

    forAll(inputs)(v => SemanticVersion.parse(v.encode).get shouldBe v)
  }
}
