package ch.epfl.scala.index.model
package release

import org.scalatest._

class SbtInstallTests extends FunSuite {
  test("crossFull") {
    val obtained =
      release(
        groupId = "org.scalamacros",
        artifactId = "paradise_2.12.3",
        version = "2.1.1",
        artifactName = "paradise",
        target = Some(
          ScalaTarget.scala(
            scalaVersion = SemanticVersion("2.12.3").get
          )
        )
      ).sbtInstall

    val expected =
      """libraryDependencies += "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full""""

    assert(expected == obtained)
  }

  test("Scala.js / Scala-Native") {
    val obtained =
      release(
        groupId = "org.scala-js",
        artifactId = "scalajs-dom_sjs0.6_2.12",
        version = "0.9.3",
        artifactName = "scalajs-dom",
        target = Some(
          ScalaTarget.scalaJs(
            scalaVersion = SemanticVersion("2.12").get,
            scalaJsVersion = SemanticVersion("0.6").get
          )
        )
      ).sbtInstall

    val expected =
      """libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.3""""

    assert(expected == obtained)
  }

  test("sbt-plugin") {
    val obtained =
      release(
        groupId = "com.typesafe.sbt",
        artifactId = "sbt-native-packager_2.10_0.13",
        version = "1.2.2",
        artifactName = "sbt-native-packager",
        target = Some(
          ScalaTarget.sbt(
            scalaVersion = SemanticVersion("2.10").get,
            sbtVersion = SemanticVersion("0.13").get
          )
        )
      ).sbtInstall

    val expected =
      """addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.2")"""

    assert(expected == obtained)
  }

  test("resolvers") {
    val obtained =
      release(
        groupId = "underscoreio",
        artifactId = "doodle_2.11",
        version = "0.8.2",
        artifactName = "doodle",
        target = Some(
          ScalaTarget.scala(
            scalaVersion = SemanticVersion("2.11").get
          )
        ),
        resolver = Some(BintrayResolver("noelwelsh", "maven"))
      ).sbtInstall

    val expected =
      """|libraryDependencies += "underscoreio" %% "doodle" % "0.8.2"
         |resolvers += Some(Resolver.bintrayRepo("noelwelsh", "maven"))""".stripMargin

    assert(expected == obtained)
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

    assert(expected == obtained)
  }

  test("non-standard") {}

  def release(groupId: String,
              artifactId: String,
              version: String,
              artifactName: String,
              target: Option[ScalaTarget],
              isNonStandardLib: Boolean = false,
              resolver: Option[Resolver] = None) = {
    Release(
      maven = MavenReference(
        groupId = groupId,
        artifactId = artifactId,
        version = version
      ),
      reference = Release.Reference(
        artifact = artifactName,
        version = SemanticVersion(version).get,
        target = target,
        // Not necessary for the test
        organization = "GitHub-Org",
        repository = "GitHub-Repo"
      ),
      resolver = resolver,
      isNonStandardLib = isNonStandardLib,
      // default/elasticsearch fields
      name = None,
      description = None,
      released = None,
      licenses = Set(),
      id = None,
      liveData = false,
      scalaDependencies = Seq(),
      javaDependencies = Seq(),
      reverseDependencies = Seq(),
      internalDependencies = Seq(),
      targetType = "",
      scalaVersion = None,
      scalaJsVersion = None,
      scalaNativeVersion = None,
      sbtVersion = None
    )
  }
}
