package ch.epfl.scala.index.model
package release

import org.scalatest.funsuite.AnyFunSuite

class MillInstallTests extends AnyFunSuite with TestHelpers {
  test("mill install dependency pattern") {
    val obtained =
      release(
        groupId = "org.http4s",
        version = "0.18.12",
        artifactId = "http4s-core_2.12",
        artifactName = "http4s-core",
        target = Some(
          ScalaJvm(
            languageVersion = ScalaVersion(PatchBinary(2, 12, 3))
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
          ScalaJvm(
            languageVersion = ScalaVersion.`2.11`
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
