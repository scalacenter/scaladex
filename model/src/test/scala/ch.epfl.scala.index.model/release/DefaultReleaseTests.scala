package ch.epfl.scala.index.model
package release

import utest._
import ai.x.diff.DiffShow
import ai.x.diff.conversions._

object DefaultReleaseTests extends TestSuite{

  val tests = this{
    "latest version pre release scala"-{
      val organization = "typelevel"
      val repository = "cats"
      val groupdId = "org.typelevel"
      val releases =
        List(
          ("cats-core_2.11"       , "0.6.0"   ),
          ("cats-core_2.11"       , "0.6.0-M2"),
          ("cats-core_2.11"       , "0.6.0-M1"),
          ("cats-core_2.11"       , "0.5.0"   ),
          ("cats-core_2.11"       , "0.4.1"   ),
          ("cats-core_2.11"       , "0.4.0"   ),
          ("cats-core_2.10"       , "0.6.0"   ),
          ("cats-core_2.10"       , "0.6.0-M2"),
          ("cats-core_2.10"       , "0.6.0-M1"),
          ("cats-core_2.10"       , "0.5.0"   ),
          ("cats-core_2.10"       , "0.4.1"   ),
          ("cats-core_2.10"       , "0.4.0"   ),
          ("cats-core_sjs0.6_2.11", "0.6.0"   ),
          ("cats-core_sjs0.6_2.11", "0.6.0-M2"),
          ("cats-core_sjs0.6_2.11", "0.6.0-M1"),
          ("cats-core_sjs0.6_2.11", "0.5.0"   ),
          ("cats-core_sjs0.6_2.11", "0.4.1"   ),
          ("cats-core_sjs0.6_2.11", "0.4.0"   ),
          ("cats-core_sjs0.6_2.10", "0.6.0"   ),
          ("cats-core_sjs0.6_2.10", "0.6.0-M2"),
          ("cats-core_sjs0.6_2.10", "0.6.0-M1"),
          ("cats-core_sjs0.6_2.10", "0.5.0"   ),
          ("cats-core_sjs0.6_2.10", "0.4.1"   ),
          ("cats-core_sjs0.6_2.10", "0.4.0"   )
        ).map{ case (artifactId, rawVersion) =>
          for {
            (artifact, target) <- Artifact(artifactId)
            version <- SemanticVersion(rawVersion)
          } yield   (artifactId, rawVersion, artifact, target, version)
        }.flatten.map{ case (artifactId, rawVersion, artifact, target, version) =>
          Release(
            MavenReference(groupdId, artifactId, rawVersion),
            Release.Reference(organization, repository, artifact, version, target)
          )
        }

      val result = DefaultRelease(Project(organization, repository), ReleaseSelection(None, None, None), releases)
      val expected =
        Some(ReleaseOptions(
          artifacts = List(
            "cats-core"
          ),
          versions = List(
            SemanticVersion("0.6.0"   ).get,
            SemanticVersion("0.6.0-M2").get,
            SemanticVersion("0.6.0-M1").get,
            SemanticVersion("0.5.0"   ).get,
            SemanticVersion("0.4.1"   ).get,
            SemanticVersion("0.4.0"   ).get
          ),
          targets = List(
            ScalaTarget(SemanticVersion("2.11").get),
            ScalaTarget(SemanticVersion("2.10").get),
            ScalaTarget(SemanticVersion("2.11").get, Some(SemanticVersion("0.6").get)),
            ScalaTarget(SemanticVersion("2.10").get, Some(SemanticVersion("0.6").get))
          ),
          release = Release(
            MavenReference(groupdId, "cats-core_2.11", "0.6.0"),
            Release.Reference(
              organization,
              repository,
              "cats-core",
              SemanticVersion("0.6.0").get,
              ScalaTarget(SemanticVersion("2.11").get)
            )
          )
        ))

      implicit def longDiffShow = DiffShow.primitive[Long](_.toString)
      implicit def semanticVersionShow = DiffShow.primitive[SemanticVersion](_.toString)

      if(result != expected){
        println(DiffShow.diff[Option[ReleaseOptions]](expected, result).string)
      }
    }
  }
}


