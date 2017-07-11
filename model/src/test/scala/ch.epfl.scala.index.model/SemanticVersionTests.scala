package ch.epfl.scala.index.model

import org.scalatest._

class SemanticVersionTests extends FunSpec {
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
          SemanticVersion(1, 1, Some(1)),
          SemanticVersion(1, 0, Some(1)),
          SemanticVersion(1, 0, Some(1), None, Some(ReleaseCandidate(2))),
          SemanticVersion(1, 0, Some(1), None, Some(ReleaseCandidate(1))),
          SemanticVersion(1, 0, Some(1), None, Some(Milestone(2))),
          SemanticVersion(1, 0, Some(1), None, Some(Milestone(1))),
          SemanticVersion(1, 0, Some(1), None, Some(OtherPreRelease("BLABLA")))
        )
      )
    }

    describe("show") {
      describe("binary") {
        // relaxed semantic version
        it("major") {
          assert(binary("1") == Some("1.0"))
        }

        // relaxed semantic version
        it("major.minor") {
          assert(binary("1.2") == Some("1.2"))
        }

        it("major.minor.patch") {
          assert(binary("1.2.3") == Some("1.2"))
        }

        // relaxed semantic version
        it("major.minor.patch.patch2") {
          assert(binary("1.2.3.4") == Some("1.2"))
        }

        it("major.minor.patch-rc") {
          assert(binary("1.2.3-RC5") == Some("1.2.3-RC5"))
        }

        it("major.minor.patch-m") {
          assert(binary("1.2.3-M6") == Some("1.2.3-M6"))
        }

        it("major.minor.patch-xyz") {
          assert(binary("1.1.1-xyz") == Some("1.1.1-xyz"))
        }

        it("major.minor.patch+meta") {
          assert(binary("1.1.1+some.meta~data") == Some("1.1"))
        }

        it("git commit") {
          assert(binary("13e7afa9c1817d45b2989e545b2e9ead21d00cef") == None)
        }

        // relaxed semantic version
        it("v sufix") {
          assert(binary("v1") == Some("1.0"))
        }
      }

      describe("full or toString") {
        // relaxed semantic version
        it("major") {
          assert(full("1") == Some("1.0"))
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
          assert(full("v1") == Some("1.0"))
        }
      }
    }

    describe("parsing") {
      // relaxed semantic version
      it("major") {
        assert(parseVersion("1") == Some(SemanticVersion(1)))
      }

      // relaxed semantic version
      it("major.minor") {
        assert(parseVersion("1.2") == Some(SemanticVersion(1, 2)))
      }

      it("major.minor.patch") {
        assert(parseVersion("1.2.3") == Some(SemanticVersion(1, 2, Some(3))))
      }

      // relaxed semantic version
      it("major.minor.patch.patch2") {
        assert(
          parseVersion("1.2.3.4") == Some(
            SemanticVersion(1, 2, Some(3), Some(4))
          )
        )
      }

      it("major.minor.patch-rc") {
        assert(
          parseVersion("1.2.3-RC5") == Some(
            SemanticVersion(1, 2, Some(3), None, Some(ReleaseCandidate(5)))
          )
        )
      }

      it("major.minor.patch-m") {
        assert(
          parseVersion("1.2.3-M6") == Some(
            SemanticVersion(1, 2, Some(3), None, Some(Milestone(6)))
          )
        )
      }

      it("major.minor.patch-xyz") {
        assert(
          parseVersion("1.1.1-xyz") == Some(
            SemanticVersion(1, 1, Some(1), None, Some(OtherPreRelease("xyz")))
          )
        )
      }

      it("major.minor.patch+meta") {
        assert(
          parseVersion("1.1.1+some.meta~data") == Some(
            SemanticVersion(
              major = 1,
              minor = 1,
              patch = Some(1),
              patch2 = None,
              preRelease = None,
              metadata = Some("some.meta~data")
            )
          )
        )
      }

      it("git commit") {
        assert(
          parseVersion("13e7afa9c1817d45b2989e545b2e9ead21d00cef") == None
        )
      }

      // relaxed semantic version
      it("v sufix") {
        assert(
          parseVersion("v1") == Some(SemanticVersion(1))
        )
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
