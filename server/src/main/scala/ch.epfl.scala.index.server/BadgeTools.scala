package ch.epfl.scala.index.server

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.model.SemanticVersion

object BadgeTools {
  def mostRecentByScalaVersion(
      platform: Platform.PlatformType,
      platformVersion: BinaryVersion
  )(rels: Seq[NewRelease]): List[(String, SemanticVersion)] = {
    val scalaVtoSemV =
      rels
        .filter { res =>
          val plat = res.platform
          plat.platformType == platform && plat.platformVersion.isDefined && plat.platformVersion.get == platformVersion
        }
        // Get is safe because scalaVersion is None only if the Platform is Java, handled by previous case
        .map(rel => rel.platform.scalaVersion.get -> rel.version)

    scalaVtoSemV
      .groupMap(_._1.family)(_._2)
      .map { case (k, versions) => k -> versions.max }
      .toList
      .sortBy(_._1)(implicitly[Ordering[String]].reverse)
  }

  def mostRecentByScalaVersionAndPlatVersion(
      platform: Platform.PlatformType
  )(
      rels: Seq[NewRelease]
  ): (Platform.PlatformType, BinaryVersion, List[(String, SemanticVersion)]) = {
    // retain only artifacts that match the specified platform
    val forThisPlatform = rels.filter { rel =>
      val plat = rel.platform
      plat.platformType == platform
    }

    // Get is safe as no Java arts should arrive here
    val maxPlatVers = forThisPlatform.map(_.platform.platformVersion.get).max
    val recentReleases = forThisPlatform.filter(
          _.platform.platformVersion.get == maxPlatVers
        )
    val tuples = mostRecentByScalaVersion(platform, maxPlatVers)(recentReleases)
    (platform, maxPlatVers, tuples)
  }
}
