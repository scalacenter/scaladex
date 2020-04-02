package ch.epfl.scala.index.model
package release

import org.scalatest._

class DefaultReleaseTests extends FunSpec with Matchers {

  def emptyRelease(maven: MavenReference,
                   reference: Release.Reference): Release =
    Release(
      maven,
      reference,
      resolver = None,
      name = None,
      description = None,
      released = None,
      licenses = Set(),
      isNonStandardLib = false,
      id = None,
      liveData = false,
      scalaDependencies = Seq(),
      javaDependencies = Seq(),
      reverseDependencies = Seq(),
      internalDependencies = Seq(),
      targetType = "JVM",
      fullScalaVersion = None,
      scalaVersion = None,
      scalaJsVersion = None,
      scalaNativeVersion = None,
      sbtVersion = None
    )

  def prepare(organization: String,
              repository: String,
              groupdId: String,
              releases: List[(String, String)]): Set[Release] = {
    releases
      .flatMap {
        case (artifactId, rawVersion) =>
          for {
            Artifact(artifact, target) <- Artifact.parse(artifactId)
            version <- SemanticVersion(rawVersion)
          } yield (artifactId, rawVersion, artifact, target, version)
      }
      .map {
        case (artifactId, rawVersion, artifact, target, version) =>
          emptyRelease(
            MavenReference(groupdId, artifactId, rawVersion),
            Release.Reference(organization,
                              repository,
                              artifact,
                              version,
                              Some(target))
          )
      }
      .toSet
  }

  describe("Default Release") {
    it("latest version pre release scala") {

      val organization = "typelevel"
      val repository = "cats"
      val groupdId = "org.typelevel"
      val releases = prepare(
        organization,
        repository,
        groupdId,
        List(
          ("cats-core_2.11", "0.6.0"),
          ("cats-core_2.11", "0.6.0-M2"),
          ("cats-core_2.11", "0.6.0-M1"),
          ("cats-core_2.11", "0.5.0"),
          ("cats-core_2.11", "0.4.1"),
          ("cats-core_2.11", "0.4.0"),
          ("cats-core_2.10", "0.6.0"),
          ("cats-core_2.10", "0.6.0-M2"),
          ("cats-core_2.10", "0.6.0-M1"),
          ("cats-core_2.10", "0.5.0"),
          ("cats-core_2.10", "0.4.1"),
          ("cats-core_2.10", "0.4.0"),
          ("cats-core_sjs0.6_2.11", "0.6.0"),
          ("cats-core_sjs0.6_2.11", "0.6.0-M2"),
          ("cats-core_sjs0.6_2.11", "0.6.0-M1"),
          ("cats-core_sjs0.6_2.11", "0.5.0"),
          ("cats-core_sjs0.6_2.11", "0.4.1"),
          ("cats-core_sjs0.6_2.11", "0.4.0"),
          ("cats-core_sjs0.6_2.10", "0.6.0"),
          ("cats-core_sjs0.6_2.10", "0.6.0-M2"),
          ("cats-core_sjs0.6_2.10", "0.6.0-M1"),
          ("cats-core_sjs0.6_2.10", "0.5.0"),
          ("cats-core_sjs0.6_2.10", "0.4.1"),
          ("cats-core_sjs0.6_2.10", "0.4.0")
        )
      )

      val result =
        DefaultRelease(repository,
                       ReleaseSelection.empty,
                       releases,
                       None,
                       defaultStableVersion = true)

      val versions: List[SemanticVersion] =
        List(
          SemanticVersion("0.6.0").get,
          SemanticVersion("0.6.0-M2").get,
          SemanticVersion("0.6.0-M1").get,
          SemanticVersion("0.5.0").get,
          SemanticVersion("0.4.1").get,
          SemanticVersion("0.4.0").get
        )

      val targets: List[ScalaTarget] =
        List(
          ScalaJs(SemanticVersion("2.11").get, SemanticVersion("0.6").get),
          ScalaJs(SemanticVersion("2.10").get, SemanticVersion("0.6").get),
          ScalaJvm(SemanticVersion("2.11").get),
          ScalaJvm(SemanticVersion("2.10").get)
        )

      val expected: Option[ReleaseOptions] =
        Some(
          ReleaseOptions(
            artifacts = List(
              "cats-core"
            ),
            versions = versions,
            targets = targets,
            release = emptyRelease(
              MavenReference(groupdId, "cats-core_2.11", "0.6.0"),
              Release.Reference(
                organization,
                repository,
                "cats-core",
                SemanticVersion("0.6.0").get,
                Some(ScalaJvm(SemanticVersion("2.11").get))
              )
            )
          )
        )

      result shouldBe expected
    }

    it("selected artifact") {
      val organization = "akka"
      val repository = "akka"
      val groupdId = "com.typesafe.akka"
      val releases =
        prepare(organization,
                repository,
                groupdId,
                List(
                  ("akka-distributed-data-experimental_2.11", "2.4.8"),
                  ("akka-actors_2.11", "2.4.8")
                ))

      val result = DefaultRelease(
        repository,
        ReleaseSelection(
          artifact = Some("akka-distributed-data-experimental"),
          target = None,
          version = None,
          selected = None
        ),
        releases,
        None,
        defaultStableVersion = true
      )

      val expected =
        Some(
          ReleaseOptions(
            artifacts = List(
              "akka-actors",
              "akka-distributed-data-experimental"
            ),
            versions = List(
              SemanticVersion("2.4.8").get
            ),
            targets = List(
              ScalaJvm(SemanticVersion("2.11").get)
            ),
            release = emptyRelease(
              MavenReference(groupdId,
                             "akka-distributed-data-experimental_2.11",
                             "2.4.8"),
              Release.Reference(
                organization,
                repository,
                "akka-distributed-data-experimental",
                SemanticVersion("2.4.8").get,
                Some(ScalaJvm(SemanticVersion("2.11").get))
              )
            )
          )
        )

      assert(result == expected)
    }

    it("scalafix ordering") {
      // 0.5.3 on 2.12 vs 0.3.4 on 2.12.2

      val organization = "scalacenter"
      val repository = "scalafix"
      val groupdId = "ch.epfl.scala"
      val releases = prepare(
        organization,
        repository,
        groupdId,
        List(
          ("scalafix-core_2.12.2", "0.3.4"),
          ("scalafix-core_2.12", "0.5.3")
        )
      )

      val result =
        DefaultRelease(repository,
                       ReleaseSelection.empty,
                       releases,
                       None,
                       defaultStableVersion = true)

      val versions: List[SemanticVersion] =
        List(
          SemanticVersion("0.3.4").get,
          SemanticVersion("0.5.3").get
        )

      val targets: List[ScalaTarget] =
        List(
          ScalaJvm(SemanticVersion("2.12.2").get),
          ScalaJvm(SemanticVersion("2.12").get)
        )

      val expected: Option[ReleaseOptions] =
        Some(
          ReleaseOptions(
            artifacts = List(
              "scalafix-core"
            ),
            versions = versions,
            targets = targets,
            release = emptyRelease(
              MavenReference(groupdId, "scalafix-core_2.12", "0.5.3"),
              Release.Reference(
                organization,
                repository,
                "scalafix-core",
                SemanticVersion("0.5.3").get,
                Some(ScalaJvm(SemanticVersion("2.12").get))
              )
            )
          )
        )

      assert(
        result.get.release.reference.version == SemanticVersion("0.5.3").get
      )
    }
  }
}
