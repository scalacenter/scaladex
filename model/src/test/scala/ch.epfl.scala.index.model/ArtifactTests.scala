package ch.epfl.scala.index.model

import release._

object ArtifactTests extends org.specs2.mutable.Specification {
  "parsing artifacts" >> {
    "scalajs" >> {
      Artifact("cats-core_sjs0.6_2.11") ==== Some(
        (
          "cats-core",
          ScalaTarget.scalaJs(SemanticVersion(2, 11), SemanticVersion(0, 6))
        )
      )
    }
    "scala-native" >> {
      Artifact("cats-core_native0.1_2.11") ==== Some(
        (
          "cats-core",
          ScalaTarget.scalaNative(SemanticVersion(2, 11), SemanticVersion(0, 1))
        )
      )
    }
    "scala rc" >> {
      Artifact("akka-remote-tests_2.11.0-RC4") ==== Some(
        (
          "akka-remote-tests",
          ScalaTarget.scala(
            SemanticVersion(2, 11, Some(0), preRelease = Some(ReleaseCandidate(4)))
          )
        )
      )
    }
    "not using sbt convention" >> {
      Artifact("sparrow") ==== None
    }
    "special case" >> {
      Artifact("banana_jvm_2.11") ==== Some(
        (
          "banana_jvm",
          ScalaTarget.scala(SemanticVersion(2, 11))
        )
      )
    }
  }
}
