package scaladex.core.model

import scaladex.core.test.Values.*

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class VersionTests extends AsyncFunSpec with Matchers with TableDrivenPropertyChecks:
  it("should parse any version") {
    val inputs = Table(
      ("input", "output"),
      ("1", Version(1)),
      ("2.12", Version(2, 12)),
      ("0.6", Version(0, 6)),
      ("2.13.0", Version(2, 13, 0)),
      ("0.4.0", Version(0, 4, 0)),
      ("0.4.0-M2", Version(0, 4, 0, Milestone(2))),
      ("0.23.0-RC1", Version(0, 23, 0, ReleaseCandidate(1))),
      ("3.0.0-M1", Version(3, 0, 0, Milestone(1))),
      ("1.1-M1", Version.SemanticLike(1, Some(1), preRelease = Some(Milestone(1)))),
      ("1.2.3.4", Version.SemanticLike(1, Some(2), Some(3), Some(4))),
      ("1.1.1-xyz", Version.SemanticLike(1, Some(1), Some(1), preRelease = Some(OtherPreRelease("xyz")))),
      ("1.1.1+some.meta~data", Version.SemanticLike(1, Some(1), Some(1), metadata = Some("some.meta~data")))
    )

    forAll(inputs)((input, output) => Version(input) shouldBe output)
  }

  it("should be ordered") {
    val inputs = Table[Version, Version](
      ("lower", "higher"),
      (Version(1), Version(2)),
      (Version(1, 1), Version(1)),
      (Version(1), Version(2, 1)),
      (Version.SemanticLike(1, Some(2), preRelease = Some(Milestone(1))), Version(1, 2)),
      (Version(1, 2, 0, Milestone(1)), Version(1, 2)),
      (Version(1), Version.SemanticLike(2, Some(0), preRelease = Some(Milestone(1)))),
      (Version.Custom("abc"), Version(1, 2, 3))
    )

    forAll(inputs)((lower, higher) => lower shouldBe <(higher))
  }

  it("should allow us to prefer stable over pre-releases") {
    val versions = Seq(`7.0.0`, `7.1.0`, `7.2.0-PREVIEW.1`)
    versions.max shouldBe `7.2.0-PREVIEW.1`
    versions.max(Version.PreferStable) shouldBe `7.1.0`
  }

  it("should encode and decode any version") {
    val inputs = Table[Version](
      "semanticVersion",
      Version(2345),
      Version(1, 3),
      Version.SemanticLike(1, Some(2), Some(3), Some(4))
    )

    forAll(inputs)(v => Version(v.value) shouldBe v)
  }
end VersionTests
