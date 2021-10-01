package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.Milestone
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.BintrayResolver
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.PatchBinary
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.PreReleaseBinary
import ch.epfl.scala.index.model.release.Resolver
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class NewReleaseTests extends AnyFunSpec with Matchers {
  describe("sbtInstall") {
    it("crossFull") {
      val obtained =
        release(
          groupId = "org.scalamacros",
          artifactId = "paradise_2.12.3",
          version = "2.1.1",
          artifactName = ArtifactName("paradise"),
          platform = Platform.ScalaJvm(
            ScalaVersion(PatchBinary(2, 12, 3))
          )
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full"""

      assert(expected == obtained)
    }

    it("binary") {
      val obtained =
        release(
          groupId = "org.scalaz",
          artifactId = "scalaz-core_2.13.0-M1",
          version = "7.2.14",
          artifactName = ArtifactName("scalaz-core"),
          platform = Platform.ScalaJvm(
            ScalaVersion(PreReleaseBinary(2, 13, Some(0), Milestone(1)))
          )
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.14""""

      assert(expected == obtained)
    }

    it("scala3") {
      val obtained =
        release(
          groupId = "org.typelevel",
          artifactId = "circe_cats-core_3.0.0-M1",
          version = "2.3.0-M2",
          artifactName = ArtifactName("circe_cats-core"),
          platform = Platform.ScalaJvm(
            Scala3Version(PreReleaseBinary(3, 0, Some(0), Milestone(1)))
          )
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.typelevel" %% "circe_cats-core" % "2.3.0-M2""""

      assertResult(expected)(obtained)
    }

    it("Scala.js / Scala-Native") {
      val obtained =
        release(
          groupId = "org.scala-js",
          artifactId = "scalajs-dom_sjs0.6_2.12",
          version = "0.9.3",
          artifactName = ArtifactName("scalajs-dom"),
          platform =
            Platform.ScalaJs(ScalaVersion.`2.12`, Platform.ScalaJs.`0.6`)
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.3""""

      assert(expected == obtained)
    }

    it("sbt-plugin") {
      val obtained =
        release(
          groupId = "com.typesafe.sbt",
          artifactId = "sbt-native-packager_2.10_0.13",
          version = "1.2.2",
          artifactName = ArtifactName("sbt-native-packager"),
          platform =
            Platform.SbtPlugin(ScalaVersion.`2.10`, Platform.SbtPlugin.`0.13`)
        ).sbtInstall

      val expected =
        """addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.2")"""

      assert(expected == obtained)
    }

    it("resolvers") {
      val obtained =
        release(
          groupId = "underscoreio",
          artifactId = "doodle_2.11",
          version = "0.8.2",
          artifactName = ArtifactName("doodle"),
          platform = Platform.ScalaJvm(
            ScalaVersion.`2.11`
          ),
          resolver = Some(BintrayResolver("noelwelsh", "maven"))
        ).sbtInstall

      val expected =
        """|libraryDependencies += "underscoreio" %% "doodle" % "0.8.2"
           |resolvers += Resolver.bintrayRepo("noelwelsh", "maven")""".stripMargin

      assert(expected == obtained)
    }

    it("Java") {
      val obtained =
        release(
          groupId = "com.typesafe",
          artifactId = "config",
          version = "1.3.1",
          artifactName = ArtifactName("config"),
          platform = Platform.Java
        ).sbtInstall

      val expected =
        """libraryDependencies += "com.typesafe" %% "config" % "1.3.1""""

      assert(expected == obtained)
    }
  }
  describe("millInstall") {
    it("mill install dependency pattern") {
      val obtained =
        release(
          groupId = "org.http4s",
          version = "0.18.12",
          artifactId = "http4s-core_2.12",
          artifactName = ArtifactName("http4s-core"),
          platform = Platform.ScalaJvm(
            ScalaVersion(PatchBinary(2, 12, 3))
          )
        ).millInstall

      val expected =
        """ivy"org.http4s::http4s-core:0.18.12""""

      assert(expected == obtained)
    }

    it("resolvers") {
      val obtained =
        release(
          groupId = "underscoreio",
          artifactId = "doodle_2.11",
          version = "0.8.2",
          artifactName = ArtifactName("doodle"),
          platform = Platform.ScalaJvm(
            ScalaVersion.`2.11`
          ),
          resolver = Some(BintrayResolver("noelwelsh", "maven"))
        ).millInstall

      val expected =
        """|ivy"underscoreio::doodle:0.8.2"
           |MavenRepository("https://dl.bintray.com/noelwelsh/maven")""".stripMargin

      assert(expected == obtained)
    }
  }
  private def release(
      groupId: String,
      artifactId: String,
      version: String,
      platform: Platform,
      artifactName: ArtifactName,
      resolver: Option[Resolver] = None
  ) =
    NewRelease(
      MavenReference(
        groupId,
        artifactId,
        version
      ),
      version = SemanticVersion.tryParse(version).get,
      organization = Organization(""),
      repository = Repository(""),
      artifactName = artifactName,
      platform = platform,
      description = None,
      released = None,
      resolver = resolver,
      licenses = Set(),
      isNonStandardLib = false
    )
}
