package ch.epfl.scala.index.server

import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.model.{Release, SemanticVersion}

import scala.collection.immutable.{SortedMap, SortedSet}

object BadgesSupport {
  def summaryOfLatestVersions(
      allAvailableReleases: Seq[Release],
      specificArtifact: String,
      specificTargetType: ScalaTargetType
  ): String = summaryOfLatestVersions((for {
    release <- allAvailableReleases if release.isValid
    ref = release.reference if ref.artifact == specificArtifact
    scalaTarget <- ref.target if scalaTarget.targetType == specificTargetType
  } yield ref.version -> scalaTarget).groupMap(_._1)(_._2))

  def summaryOfLatestVersions(
      scalaTargetsByArtifactVersion: Map[SemanticVersion, Seq[ScalaTarget]]
  ): String = {
    val allTargets = scalaTargetsByArtifactVersion.values.flatten.toSet

    val latestScalaVersionsOfAvailableBinaryVersions =
      allTargets
        .map(_.languageVersion)
        .groupBy(_.family)
        .view
        .mapValues(_.max)
        .values
        .toSet

    val latestArtifactVersionByLanguageVersion = (for {
      (artifactVersion, scalaTargets) <- scalaTargetsByArtifactVersion.toSeq
      scalaTarget <- scalaTargets
      if latestScalaVersionsOfAvailableBinaryVersions.contains(
        scalaTarget.languageVersion
      )
    } yield scalaTarget.languageVersion -> artifactVersion)
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.max)
      .toMap

    val scalaSupportByLatestSupportingArtifactVersion = SortedMap.from(
      latestArtifactVersionByLanguageVersion.groupMap(_._2)(_._1).view.map {
        case (artifactVersion, notableLanguageVersions) =>
          val notableLanguageVersionsSet = notableLanguageVersions.toSet
          artifactVersion -> ScalaSupport(
            scalaTargetsByArtifactVersion(artifactVersion)
              .filter(st => notableLanguageVersionsSet(st.languageVersion))
              .toSet
          )
      }
    )(SemanticVersion.ordering.reverse)

    (for (
      (artifactVersion, scalaSupport) <-
        scalaSupportByLatestSupportingArtifactVersion
    ) yield s"$artifactVersion (${scalaSupport.summary})").mkString(", ")
  }

  case class ScalaSupport(
      scalaTargets: Set[ScalaTarget]
  ) {
    val scalaTargetsByLanguageVersion: Map[LanguageVersion, Set[ScalaTarget]] =
      scalaTargets.groupBy(_.languageVersion)

    val languageVersions: SortedSet[LanguageVersion] =
      SortedSet.from(scalaTargetsByLanguageVersion.keySet)(
        LanguageVersion.ordering.reverse
      )

    val platformEditionsSupportedForAllLanguageVersions: Set[PlatformEdition] =
      scalaTargetsByLanguageVersion.values
        .map(_.collect { case st: ScalaTargetWithPlatformBinaryVersion =>
          st.platformEdition
        })
        .reduce(_ & _)

    val platformBinaryVersionsByTargetType
        : SortedMap[ScalaTargetType, SortedSet[BinaryVersion]] =
      SortedMap.from(
        platformEditionsSupportedForAllLanguageVersions
          .groupMap(_.targetType)(_.version)
          .view
          .mapValues(SortedSet.from(_)(BinaryVersion.ordering.reverse))
      )

    val targetsSummary: Option[String] =
      Option.when(platformBinaryVersionsByTargetType.nonEmpty)(
        " - " + platformBinaryVersionsByTargetType
          .map { case (targetType, platformBinaryVersions) =>
            s"$targetType ${platformBinaryVersions.mkString("+")}"
          }
          .mkString(", ")
      )

    val summary: String =
      s"Scala ${languageVersions.mkString(", ")}${targetsSummary.mkString}"
  }
}
