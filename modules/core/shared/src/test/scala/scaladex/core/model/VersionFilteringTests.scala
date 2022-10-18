package scaladex.core.model

import org.scalatest.funsuite.AnyFunSuite

class VersionFilteringTests extends AnyFunSuite {
  test("GeoTrellis") {
    val versions = List(
      "1.2.0-astraea.9",
      "1.1.1",
      "1.1.0",
      "1.1.0-RC6",
      "1.1.0-RC5",
      "1.1.0-RC4",
      "1.1.0-RC3",
      "1.1.0-RC2",
      "1.1.0-RC1",
      "1.1.0-astraea.8",
      "1.1.0-astraea.7",
      "1.1.0-astraea.6",
      "1.1.0-astraea.5",
      "1.1.0-astraea.4",
      "1.1.0-astraea.3",
      "1.0.0",
      "1.0.0-RC3",
      "1.0.0-hdfs-tuning-1",
      "1.0.0-fb57dc4",
      "1.0.0-f8ed373",
      "1.0.0-f7daf3d",
      "1.0.0-033d5c9",
      "0.10.3",
      "0.10.2",
      "0.10.1",
      "0.10.0",
      "0.10.0-RC4",
      "0.10.0-RC3",
      "0.10.0-RC2",
      "0.10.0-RC1",
      "0.10.0-M2",
      "0.10.0-M1",
      "0.10.0-test",
      "0.10.0-cb236ac",
      "0.10.0-OWM-M0-J7",
      "0.10.0-OWM-M0",
      "0.9.2",
      "0.9.1",
      "0.9.0",
      "0.9.0-RC5",
      "0.9.0-RC4",
      "0.9.0-RC3",
      "0.9.0-RC2",
      "0.9.0-RC1",
      "0.8.2-RC2",
      "0.8.2-RC1",
      "0.8.1",
      "0.8.1-RC3",
      "0.8.1-RC2",
      "0.8.1-RC1"
    ).flatMap(SemanticVersion.parse)

    val obtained = versions.filter(_.isSemantic)

    val expected = List(
      "1.1.1",
      "1.1.0",
      "1.1.0-RC6",
      "1.1.0-RC5",
      "1.1.0-RC4",
      "1.1.0-RC3",
      "1.1.0-RC2",
      "1.1.0-RC1",
      "1.0.0",
      "1.0.0-RC3",
      "0.10.3",
      "0.10.2",
      "0.10.1",
      "0.10.0",
      "0.10.0-RC4",
      "0.10.0-RC3",
      "0.10.0-RC2",
      "0.10.0-RC1",
      "0.10.0-M2",
      "0.10.0-M1",
      "0.9.2",
      "0.9.1",
      "0.9.0",
      "0.9.0-RC5",
      "0.9.0-RC4",
      "0.9.0-RC3",
      "0.9.0-RC2",
      "0.9.0-RC1",
      "0.8.2-RC2",
      "0.8.2-RC1",
      "0.8.1",
      "0.8.1-RC3",
      "0.8.1-RC2",
      "0.8.1-RC1"
    ).flatMap(SemanticVersion.parse)

    assert((obtained.toSet -- expected.toSet).isEmpty)
    assert((expected.toSet -- obtained.toSet).isEmpty)
  }
}
