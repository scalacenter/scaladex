package ch.epfl.scala.index.model
package release

import org.scalatest._

class SbtInstallTests extends FunSuite with TestHelpers {
  test("crossFull") {
    val obtained =
      release(
        groupId = "org.scalamacros",
        artifactId = "paradise_2.12.3",
        version = "2.1.1",
        artifactName = "paradise",
        target = Some(
          ScalaJvm(
            languageVersion = ScalaVersion(PatchBinary(2, 12, 3))
          )
        )
      ).sbtInstall

    val expected =
      """libraryDependencies += "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full"""

    assert2(expected, obtained)
  }

  test("binary") {
    val obtained =
      release(
        groupId = "org.scalaz",
        artifactId = "scalaz-core_2.13.0-M1",
        version = "7.2.14",
        artifactName = "scalaz-core",
        target = Some(
          ScalaJvm(
            languageVersion =
              ScalaVersion(PreReleaseBinary(2, 13, Some(0), Milestone(1)))
          )
        )
      ).sbtInstall

    val expected =
      """libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.14""""

    assert2(expected, obtained)
  }

  test("scala3") {
    val obtained =
      release(
        groupId = "org.typelevel",
        artifactId = "circe_cats-core_3.0.0-M1",
        version = "2.3.0-M2",
        artifactName = "circe_cats-core",
        target = Some(
          ScalaJvm(
            languageVersion =
              DottyVersion(PreReleaseBinary(3, 0, Some(0), Milestone(1)))
          )
        )
      ).sbtInstall

    val expected =
      """libraryDependencies += "org.typelevel" %% "circe_cats-core" % "2.3.0-M2""""

    assertResult(expected)(obtained)
  }

  test("Scala.js / Scala-Native") {
    val obtained =
      release(
        groupId = "org.scala-js",
        artifactId = "scalajs-dom_sjs0.6_2.12",
        version = "0.9.3",
        artifactName = "scalajs-dom",
        target = Some(
          ScalaJs(
            languageVersion = ScalaVersion.`2.12`,
            scalaJsVersion = MinorBinary(0, 6)
          )
        )
      ).sbtInstall

    val expected =
      """libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.3""""

    assert2(expected, obtained)
  }

  test("sbt-plugin") {
    val obtained =
      release(
        groupId = "com.typesafe.sbt",
        artifactId = "sbt-native-packager_2.10_0.13",
        version = "1.2.2",
        artifactName = "sbt-native-packager",
        target = Some(
          SbtPlugin(
            languageVersion = ScalaVersion.`2.10`,
            sbtVersion = MinorBinary(0, 13)
          )
        )
      ).sbtInstall

    val expected =
      """addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.2")"""

    assert2(expected, obtained)
  }

  test("resolvers") {
    val obtained =
      release(
        groupId = "underscoreio",
        artifactId = "doodle_2.11",
        version = "0.8.2",
        artifactName = "doodle",
        target = Some(
          ScalaJvm(
            languageVersion = ScalaVersion.`2.11`
          )
        ),
        resolver = Some(BintrayResolver("noelwelsh", "maven"))
      ).sbtInstall

    val expected =
      """|libraryDependencies += "underscoreio" %% "doodle" % "0.8.2"
         |resolvers += Resolver.bintrayRepo("noelwelsh", "maven")""".stripMargin

    assert2(expected, obtained)
  }

  test("Java") {
    val obtained =
      release(
        groupId = "com.typesafe",
        artifactId = "config",
        version = "1.3.1",
        artifactName = "config",
        target = None
      ).sbtInstall

    val expected =
      """libraryDependencies += "com.typesafe" %% "config" % "1.3.1""""

    assert2(expected, obtained)
  }

  test("non-standard") {}
}
