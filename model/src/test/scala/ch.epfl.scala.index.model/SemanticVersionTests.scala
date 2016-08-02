package ch.epfl.scala.index.model

object SemanticVersionTests extends org.specs2.mutable.Specification {
  def parseVersion(v: String): Option[SemanticVersion] = SemanticVersion(v)
  "semantic versionning" >> {
    "ordering" >> {
      def order(versions: List[String]): List[SemanticVersion] = 
        versions.flatMap(v => parseVersion(v)).sorted(Descending[SemanticVersion])

      "small" >> {
        val versions =
          "1.0.1" ::
          "1.0.1-M1" ::
          "1.0.1-M2" ::
          "1.0.1-RC2" ::
          "1.1.1" :: 
          "1.0.1-BLABLA" ::
          "1.0.1-RC1" ::
          Nil

        order(versions) ==== List(
          SemanticVersion(1, 1, Some(1)),
          SemanticVersion(1, 0, Some(1)),
          SemanticVersion(1, 0, Some(1), None, Some(ReleaseCandidate(2))),
          SemanticVersion(1, 0, Some(1), None, Some(ReleaseCandidate(1))),
          SemanticVersion(1, 0, Some(1), None, Some(Milestone(2))),
          SemanticVersion(1, 0, Some(1), None, Some(Milestone(1))),
          SemanticVersion(1, 0, Some(1), None, Some(OtherPreRelease("BLABLA")))
        )
      }
    }

    "parsing" >> {
      // relaxed semantic version
      "major" >> {
        parseVersion("1") ==== Some(SemanticVersion(1))
      }

      // relaxed semantic version
      "major.minor" >> {
        parseVersion("1.2") ==== Some(SemanticVersion(1, 2))
      }

      "major.minor.patch" >> {
        parseVersion("1.2.3") ==== Some(SemanticVersion(1, 2, Some(3)))
      }

      // relaxed semantic version
      "major.minor.patch.patch2" >> {
        parseVersion("1.2.3.4") ==== Some(SemanticVersion(1, 2, Some(3), Some(4)))
      }

      "major.minor.patch-rc" >> {
        parseVersion("1.2.3-RC5") ==== Some(SemanticVersion(1, 2, Some(3), None, Some(ReleaseCandidate(5))))
      }

      "major.minor.patch-m" >> {
        parseVersion("1.2.3-M6") ==== Some(SemanticVersion(1, 2, Some(3), None, Some(Milestone(6))))
      }

      "major.minor.patch-xyz" >> {
        parseVersion("1.1.1-xyz") ==== Some(SemanticVersion(1, 1, Some(1), None, Some(OtherPreRelease("xyz"))))
      }

      "major.minor.patch+meta" >> {
        parseVersion("1.1.1+some.meta~data") ==== Some(
          SemanticVersion(
            major = 1,
            minor = 1,
            patch = Some(1),
            patch2 = None,
            preRelease = None,
            metadata = Some("some.meta~data")
          )
        )
      }

      "git commit" >> {
        parseVersion("13e7afa9c1817d45b2989e545b2e9ead21d00cef") ==== None
      }

      // relaxed semantic version
      "v sufix" >> {
        parseVersion("v1") ==== Some(SemanticVersion(1))
      }
    }
  }
}