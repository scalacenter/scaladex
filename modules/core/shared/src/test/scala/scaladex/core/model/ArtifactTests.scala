package scaladex.core.model

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact._

class ArtifactTests extends AnyFunSpec with Matchers {
  describe("sbtInstall") {
    it("crossFull") {
      val obtained =
        createArtifact(
          groupId = "org.scalamacros",
          artifactId = "paradise_2.12.3",
          version = "2.1.1",
          binaryVersion = BinaryVersion(Jvm, Scala(PatchVersion(2, 12, 3))),
          artifactName = Some(Name("paradise"))
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full"""

      obtained should contain(expected)
    }

    it("binary") {
      val obtained =
        createArtifact(
          groupId = "org.scalaz",
          artifactId = "scalaz-core_2.13.0-M1",
          version = "7.2.14",
          binaryVersion = BinaryVersion(Jvm, Scala(PreReleaseVersion(2, 13, 0, Milestone(1)))),
          artifactName = Some(Name("scalaz-core"))
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.14""""

      obtained should contain(expected)
    }

    it("scala3") {
      val obtained =
        createArtifact(
          groupId = "org.typelevel",
          artifactId = "circe_cats-core_3.0.0-M1",
          version = "2.3.0-M2",
          binaryVersion = BinaryVersion(Jvm, Scala.`3`),
          artifactName = Some(Name("circe_cats-core"))
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.typelevel" %% "circe_cats-core" % "2.3.0-M2""""

      obtained should contain(expected)
    }

    it("Scala.js / Scala-Native") {
      val obtained =
        createArtifact(
          groupId = "org.scala-js",
          artifactId = "scalajs-dom_sjs0.6_2.12",
          version = "0.9.3",
          binaryVersion = BinaryVersion(ScalaJs.`0.6`, Scala.`2.12`)
        ).sbtInstall

      val expected =
        """libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.3""""

      obtained should contain(expected)
    }

    it("sbt-plugin") {
      val obtained =
        createArtifact(
          groupId = "com.typesafe.sbt",
          artifactId = "sbt-native-packager_2.10_0.13",
          version = "1.2.2",
          binaryVersion = BinaryVersion(SbtPlugin.`0.13`, Scala.`2.10`)
        ).sbtInstall

      val expected =
        """addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.2")"""

      obtained should contain(expected)
    }

    it("resolvers") {
      val obtained =
        createArtifact(
          groupId = "underscoreio",
          artifactId = "doodle_2.11",
          version = "0.8.2",
          binaryVersion = BinaryVersion(Jvm, Scala.`2.11`),
          resolver = Some(BintrayResolver("noelwelsh", "maven"))
        ).sbtInstall

      val expected =
        """|libraryDependencies += "underscoreio" %% "doodle" % "0.8.2"
           |resolvers += Resolver.bintrayRepo("noelwelsh", "maven")""".stripMargin

      obtained should contain(expected)
    }

    it("Java") {
      val artifact = createArtifact("com.typesafe", "config", "1.3.1", BinaryVersion(Jvm, Java))
      artifact.sbtInstall should contain("""libraryDependencies += "com.typesafe" % "config" % "1.3.1"""")
    }
  }
  describe("millInstall") {
    it("mill install dependency pattern") {
      val obtained =
        createArtifact(
          groupId = "org.http4s",
          version = "0.18.12",
          artifactId = "http4s-core_2.12",
          binaryVersion = BinaryVersion(Jvm, Scala(PatchVersion(2, 12, 3)))
        ).millInstall

      val expected =
        """ivy"org.http4s::http4s-core:0.18.12""""

      obtained should contain(expected)
    }

    it("resolvers") {
      val obtained =
        createArtifact(
          groupId = "underscoreio",
          artifactId = "doodle_2.11",
          version = "0.8.2",
          binaryVersion = BinaryVersion(Jvm, Scala.`2.11`),
          resolver = Some(BintrayResolver("noelwelsh", "maven"))
        ).millInstall

      val expected =
        """|ivy"underscoreio::doodle:0.8.2"
           |MavenRepository("https://dl.bintray.com/noelwelsh/maven")""".stripMargin

      obtained should contain(expected)
    }
  }

  describe("scastieURL") {
    it("should return None if artifact is SbtPlugin") {
      createArtifact(
        groupId = "com.typesafe.sbt",
        artifactId = "sbt-native-packager_2.10_0.13",
        version = "1.2.2",
        binaryVersion = BinaryVersion(SbtPlugin.`0.13`, Scala.`2.10`)
      ).scastieURL should be(None)
    }

    it("should return None if artifact is ScalaNative") {
      createArtifact(
        groupId = "org.typelevel",
        artifactId = "cats-core_native0.4_2.13",
        version = "2.6.1",
        binaryVersion = BinaryVersion(ScalaNative.`0.4`, Scala.`2.13`)
      ).scastieURL should be(None)
    }

    it("should return None if artifact doesn't have ScalaVersion") {
      createArtifact(
        groupId = "com.typesafe",
        artifactId = "config",
        version = "2.6.1",
        binaryVersion = BinaryVersion(Jvm, Java)
      ).scastieURL should be(None)
    }

    it("should return a valid URL") {
      createArtifact(
        groupId = "org.scalameta",
        artifactId = "metals_2.13",
        version = "0.11.8",
        binaryVersion = BinaryVersion(Jvm, Scala.`2.13`),
        projectRef = Some(Project.Reference.from("scalameta", "metals"))
      ).scastieURL should be(
        Some("https://scastie.scala-lang.org/try?g=org.scalameta&a=metals&v=0.11.8&o=scalameta&r=metals&t=JVM&sv=2.13")
      )
    }

    it("should return a valid URL for a ScalaJs artifact") {
      createArtifact(
        groupId = "org.scala-js",
        artifactId = "scalajs-dom_sjs1_2.13",
        version = "2.8.0",
        binaryVersion = BinaryVersion(ScalaJs.`1.x`, Scala.`2.13`),
        projectRef = Some(Project.Reference.from("Scala.js", "scalajs-library"))
      ).scastieURL should be(
        Some(
          "https://scastie.scala-lang.org/try?g=org.scala-js&a=scalajs-dom&v=2.8.0&o=scala.js&r=scalajs-library&t=JS&sv=2.13"
        )
      )
    }
  }

  private def createArtifact(
      groupId: String,
      artifactId: String,
      version: String,
      binaryVersion: BinaryVersion,
      artifactName: Option[Artifact.Name] = None,
      resolver: Option[Resolver] = None,
      projectRef: Option[Project.Reference] = None
  ) = {
    // An artifact always have an artifactId that can be parsed, but in the case we don't really care about if it can
    // be parsed or not, we just want to test methods in artifacts like sbtInstall
    // in fact those tests don't make sense, since it's not supposed to happen except if an Artifact is created without parsing.
    val artifactIdResult =
      artifactName.map(name => ArtifactId(name, binaryVersion)).orElse(Artifact.ArtifactId.parse(artifactId)).get
    Artifact(
      groupId = GroupId(groupId),
      artifactId = artifactId,
      version = SemanticVersion.parse(version).get,
      artifactName = artifactIdResult.name,
      platform = artifactIdResult.binaryVersion.platform,
      language = artifactIdResult.binaryVersion.language,
      projectRef = projectRef.getOrElse(Project.Reference.from("", "")),
      description = None,
      releaseDate = Instant.now(),
      resolver = resolver,
      licenses = Set(),
      isNonStandardLib = false,
      fullScalaVersion = None
    )
  }
}
