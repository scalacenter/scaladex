package ch.epfl.scala.index.model
package release

object DefaultReleaseTests extends org.specs2.mutable.Specification {

  def emptyRelease(maven: MavenReference, reference: Release.Reference): Release =
    Release(
      maven,
      reference,
      resolver = None,
      name = None,
      description = None,
      released = None,
      licenses = Set(),
      nonStandardLib = false,
      id = None,
      liveData = false,
      scalaDependencies = Seq(),
      javaDependencies = Seq(),
      reverseDependencies = Seq(),
      internalDependencies = Seq(),
      targetType = "JVM",
      scalaVersion = None,
      scalaJsVersion = None,
      scalaNativeVersion = None
    )

  def prepare(organization: String,
              repository: String,
              groupdId: String,
              releases: List[(String, String)]) = {
    releases.flatMap {
      case (artifactId, rawVersion) =>
        for {
          (artifact, target) <- Artifact(artifactId)
          version <- SemanticVersion(rawVersion)
        } yield (artifactId, rawVersion, artifact, target, version)
    }.map {
      case (artifactId, rawVersion, artifact, target, version) =>
        emptyRelease(
          MavenReference(groupdId, artifactId, rawVersion),
          Release.Reference(organization, repository, artifact, version, Some(target))
        )
    }.toSet
  }

  "Default Release" >> {
    "latest version pre release scala" >> {

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
        DefaultRelease(repository, ReleaseSelection(None, None, None), releases, None, true)

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
          ScalaTarget.scalaJs(SemanticVersion("2.11").get, SemanticVersion("0.6").get),
          ScalaTarget.scalaJs(SemanticVersion("2.10").get, SemanticVersion("0.6").get),
          ScalaTarget.scala(SemanticVersion("2.11").get),
          ScalaTarget.scala(SemanticVersion("2.10").get)
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
                Some(ScalaTarget.scala(SemanticVersion("2.11").get))
              )
            )
          ))

      expected ==== result
    }

    "selected artifact" >> {
      val organization = "akka"
      val repository = "akka"
      val groupdId = "com.typesafe.akka"
      val releases = prepare(organization,
                             repository,
                             groupdId,
                             List(
                               ("akka-distributed-data-experimental_2.11", "2.4.8"),
                               ("akka-actors_2.11", "2.4.8")
                             ))

      val result = DefaultRelease(
        repository,
        ReleaseSelection(artifact = Some("akka-distributed-data-experimental"),
                         target = None,
                         version = None),
        releases,
        None,
        true
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
              ScalaTarget.scala(SemanticVersion("2.11").get)
            ),
            release = emptyRelease(
              MavenReference(groupdId, "akka-distributed-data-experimental_2.11", "2.4.8"),
              Release.Reference(
                organization,
                repository,
                "akka-distributed-data-experimental",
                SemanticVersion("2.4.8").get,
                Some(ScalaTarget.scala(SemanticVersion("2.11").get))
              )
            )
          ))

      result ==== expected
    }
  }
}
