package ch.epfl.scala.index.server

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewRelease

object BadgeTools {
  def mostRecentByScalaVersion(
      platform: Platform.PlatformType,
      platformVersion: Option[BinaryVersion]
  )(rels: Seq[NewRelease]): List[(String, SemanticVersion)] = {
    val scalaVtoSemV =
      rels
        .filter { res =>
          val plat = res.platform
          plat.platformType == platform && plat.platformVersion == platformVersion
        }
        .map(rel => rel.platform.scalaVersion.get -> rel.version)

    scalaVtoSemV
      .groupMap(_._1.family.replace("scala", ""))(
        _._2
      ) // family for scala 3 artifacts is Scala 3 rathen than 3
      .map { case (k, versions) => k -> versions.max }
      .toList
      .sortBy(_._1)(implicitly[Ordering[String]].reverse)
  }

  def mostRecentByScalaVersionAndPlatVersion(
      platform: Platform.PlatformType
  )(
      rels: Seq[NewRelease]
  ): (
      Platform.PlatformType,
      Option[BinaryVersion],
      List[(String, SemanticVersion)]
  ) = {
    // retain only artifacts that match the specified platform
    val forThisPlatform = rels.filter { rel =>
      val plat = rel.platform
      plat.platformType == platform
    }

    // Get is safe as no Java arts should arrive here
    val maxPlatVers = forThisPlatform.map(_.platform.platformVersion).max
    val recentReleases = forThisPlatform.filter(
      _.platform.platformVersion == maxPlatVers
    )
    val tuples = mostRecentByScalaVersion(platform, maxPlatVers)(recentReleases)
    (platform, maxPlatVers, tuples)
  }
}
