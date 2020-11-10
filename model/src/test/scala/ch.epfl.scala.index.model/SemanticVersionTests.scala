package ch.epfl.scala.index.model

import org.scalatest._

class SemanticVersionTests extends FunSpec with Matchers {
  describe("semantic versionning") {
    it("has an ordering") {
      def order(versions: List[String]): List[SemanticVersion] =
        versions
          .flatMap(v => SemanticVersion.tryParse(v))
          .sorted(Descending[SemanticVersion])

      val versions = List(
        "1.0.1",
        "1.0.1-M1",
        "1.0.1-M2",
        "1.0.1-RC2",
        "1.1.1",
        "1.0.1-BLABLA",
        "1.0.1-RC1"
      )

      assert(
        order(versions) == List(
          SemanticVersion(1, 1, 1),
          SemanticVersion(1, 0, 1),
          SemanticVersion(1, Some(0), Some(1), None, Some(ReleaseCandidate(2))),
          SemanticVersion(1, Some(0), Some(1), None, Some(ReleaseCandidate(1))),
          SemanticVersion(1, Some(0), Some(1), None, Some(Milestone(2))),
          SemanticVersion(1, Some(0), Some(1), None, Some(Milestone(1))),
          SemanticVersion(
            1,
            Some(0),
            Some(1),
            None,
            Some(OtherPreRelease("BLABLA"))
          )
        )
      )
    }

    describe("parsing") {
      // relaxed semantic version
      it("major") {
        SemanticVersion.tryParse("1") should contain(SemanticVersion(1))
      }

      // relaxed semantic version
      it("major.minor") {
        SemanticVersion.tryParse("1.2") should contain(SemanticVersion(1, 2))
      }

      it("major.minor.patch") {
        SemanticVersion.tryParse("1.2.3") should contain(
          SemanticVersion(1, 2, 3)
        )
      }

      // relaxed semantic version
      it("major.minor.patch.patch2") {
        SemanticVersion.tryParse("1.2.3.4") should contain(
          SemanticVersion(1, 2, 3, 4)
        )
      }

      it("major.minor.patch-rc") {
        SemanticVersion.tryParse("1.2.3-RC5") should contain(
          SemanticVersion(1, 2, 3, ReleaseCandidate(5))
        )

      }

      it("major.minor.patch-m") {
        SemanticVersion.tryParse("1.2.3-M6") should contain(
          SemanticVersion(1, 2, 3, Milestone(6))
        )
      }

      it("major.minor.patch-xyz") {
        SemanticVersion.tryParse("1.1.1-xyz") should contain(
          SemanticVersion(
            1,
            Some(1),
            Some(1),
            None,
            Some(OtherPreRelease("xyz"))
          )
        )
      }

      it("major.minor.patch+meta") {
        SemanticVersion.tryParse("1.1.1+some.meta~data") should contain(
          SemanticVersion(
            major = 1,
            minor = Some(1),
            patch = Some(1),
            metadata = Some("some.meta~data")
          )
        )
      }

      it("git commit") {
        SemanticVersion.tryParse(
          "13e7afa9c1817d45b2989e545b2e9ead21d00cef"
        ) shouldBe empty
        SemanticVersion.tryParse(
          "6988989374b307bc6a57f9a3d218fead6c4c634f"
        ) shouldBe empty
      }

      // relaxed semantic version
      it("v sufix") {
        SemanticVersion.tryParse("v1") should contain(SemanticVersion(1))
      }
    }
  }
}
