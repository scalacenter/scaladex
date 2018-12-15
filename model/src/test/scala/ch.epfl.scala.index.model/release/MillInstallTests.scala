package ch.epfl.scala.index.model
package release

import org.scalatest._

class MillInstallTests extends FunSuite with TestHelpers {
  test("mill install dependency pattern") {
    val obtained =
      release(
        groupId = "org.http4s",
        version = "0.18.12",
        artifactId = "http4s-core_2.12",
        artifactName = "http4s-core",
        target = Some(
          ScalaTarget.scala(
            scalaVersion = SemanticVersion("2.12.3").get
          )
        )
      ).millInstall

    val expected =
      """ivy"org.http4s::http4s-core:0.18.12""""

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
          ScalaTarget.scala(
            scalaVersion = SemanticVersion("2.11").get
          )
        ),
        resolver = Some(BintrayResolver("noelwelsh", "maven"))
      ).millInstall

    val expected =
      """|ivy"underscoreio::doodle:0.8.2"
         |MavenRepository("https://dl.bintray.com/noelwelsh/maven")""".stripMargin

    assert2(expected, obtained)
  }
}
