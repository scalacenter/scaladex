package ch.epfl.scala.index.model

import org.scalatest._

class SemanticVersionTests extends FunSpec with Matchers {
  describe("semantic versionning") {
    it("has an ordering") {
      def order(versions: List[String]): List[SemanticVersion] =
        versions
          .flatMap(v => parseVersion(v))
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
          SemanticVersion(1,
                          Some(0),
                          Some(1),
                          None,
                          Some(OtherPreRelease("BLABLA")))
        )
      )
    }

    describe("show") {
      describe("binary") {
        // relaxed semantic version
        it("major") {
          binary("1") should contain("1")
        }

        // relaxed semantic version
        it("major.minor") {
          binary("1.2") should contain("1.2")
        }

        it("major.minor.patch") {
          binary("1.2.3") should contain("1.2")
        }

        // relaxed semantic version
        it("major.minor.patch.patch2") {
          binary("1.2.3.4") should contain("1.2")
        }

        it("major.minor.patch-rc") {
          binary("1.2.3-RC5") should contain("1.2.3-RC5")
        }

        it("major.minor.patch-m") {
          binary("1.2.3-M6") should contain("1.2.3-M6")
        }

        it("major.minor.patch-xyz") {
          binary("1.1.1-xyz") should contain("1.1.1-xyz")
        }

        it("major.minor.patch+meta") {
          binary("1.1.1+some.meta~data") should contain("1.1")
        }

        it("git commit") {
          binary("13e7afa9c1817d45b2989e545b2e9ead21d00cef") shouldBe empty
        }

        // relaxed semantic version
        it("v sufix") {
          binary("v1") should contain("1")
        }
      }

      describe("full or toString") {
        // relaxed semantic version
        it("major") {
          full("1") should contain("1")
        }

        // relaxed semantic version
        it("major.minor") {
          assert(full("1.2") == Some("1.2"))
        }

        it("major.minor.patch") {
          assert(full("1.2.3") == Some("1.2.3"))
        }

        // relaxed semantic version
        it("major.minor.patch.patch2") {
          assert(full("1.2.3.4") == Some("1.2.3.4"))
        }

        it("major.minor.patch-rc") {
          assert(full("1.2.3-RC5") == Some("1.2.3-RC5"))
        }

        it("major.minor.patch-m") {
          assert(full("1.2.3-M6") == Some("1.2.3-M6"))
        }

        it("major.minor.patch-xyz") {
          assert(full("1.1.1-xyz") == Some("1.1.1-xyz"))
        }

        it("major.minor.patch+meta") {
          assert(full("1.1.1+some.meta~data") == Some("1.1.1+some.meta~data"))
        }

        it("git commit") {
          assert(full("13e7afa9c1817d45b2989e545b2e9ead21d00cef") == None)
        }

        // relaxed semantic version
        it("v sufix") {
          full("v1") should contain("1")
        }
      }
    }

    describe("parsing") {
      // relaxed semantic version
      it("major") {
        parseVersion("1") should contain(SemanticVersion(1))
      }

      // relaxed semantic version
      it("major.minor") {
        parseVersion("1.2") should contain(SemanticVersion(1, 2))
      }

      it("major.minor.patch") {
        parseVersion("1.2.3") should contain(SemanticVersion(1, 2, 3))
      }

      // relaxed semantic version
      it("major.minor.patch.patch2") {
        parseVersion("1.2.3.4") should contain(
          SemanticVersion(1, 2, 3, 4)
        )
      }

      it("major.minor.patch-rc") {
        parseVersion("1.2.3-RC5") should contain(
          SemanticVersion(1, 2, 3, ReleaseCandidate(5))
        )

      }

      it("major.minor.patch-m") {
        parseVersion("1.2.3-M6") should contain(
          SemanticVersion(1, 2, 3, Milestone(6))
        )
      }

      it("major.minor.patch-xyz") {
        parseVersion("1.1.1-xyz") should contain(
          SemanticVersion(1,
                          Some(1),
                          Some(1),
                          None,
                          Some(OtherPreRelease("xyz")))
        )
      }

      it("major.minor.patch+meta") {
        parseVersion("1.1.1+some.meta~data") should contain(
          SemanticVersion(
            major = 1,
            minor = Some(1),
            patch = Some(1),
            metadata = Some("some.meta~data")
          )
        )
      }

      it("git commit") {
        parseVersion("13e7afa9c1817d45b2989e545b2e9ead21d00cef") shouldBe empty
      }

      // relaxed semantic version
      it("v sufix") {
        parseVersion("v1") should contain(SemanticVersion(1))
      }
    }
  }

  private def parseVersion(v: String): Option[SemanticVersion] =
    SemanticVersion(v)
  private def binary(v: String): Option[String] =
    SemanticVersion(v).map(_.binary.toString)
  private def full(v: String): Option[String] =
    SemanticVersion(v).map(_.toString)
}
