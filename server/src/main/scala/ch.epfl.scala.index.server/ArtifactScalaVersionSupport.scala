package ch.epfl.scala.index.server

import ch.epfl.scala.index.model.{Release, SemanticVersion}
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.server.ArtifactScalaVersionSupport.ScalaSupport

import scala.collection.immutable.{SortedMap, SortedSet}

case class ArtifactScalaVersionSupport(
    scalaTargetsForAllArtifactVersions: Map[SemanticVersion, Seq[ScalaTarget]]
) {

  val allTargets: Set[ScalaTarget] =
    scalaTargetsForAllArtifactVersions.values.flatten.toSet

  val latestScalaVersionsOfAvailableBinaryVersions: Set[LanguageVersion] =
    allTargets
      .map(_.languageVersion)
      .groupBy(_.family)
      .view
      .mapValues(_.max)
      .values
      .toSet

  val latestArtifactVersionByLanguageVersion
      : Map[LanguageVersion, SemanticVersion] = (for {
    (artifactVersion, scalaTargets) <- scalaTargetsForAllArtifactVersions.toSeq
    scalaTarget <- scalaTargets
    if latestScalaVersionsOfAvailableBinaryVersions.contains(
      scalaTarget.languageVersion
    )
  } yield scalaTarget.languageVersion -> artifactVersion)
    .groupMap(_._1)(_._2)
    .view
    .mapValues(_.max)
    .toMap

  val scalaSupportByLatestSupportingArtifactVersion
      : SortedMap[SemanticVersion, ScalaSupport] =
    SortedMap.from(
      latestArtifactVersionByLanguageVersion.groupMap(_._2)(_._1).view.map {
        case (artifactVersion, notableLanguageVersions) =>
          val notableLanguageVersionsSet = notableLanguageVersions.toSet
          artifactVersion -> ScalaSupport(
            scalaTargetsForAllArtifactVersions(artifactVersion)
              .filter(st => notableLanguageVersionsSet(st.languageVersion))
              .toSet
          )
      }
    )(SemanticVersion.ordering.reverse)

  val summaryOfLatestArtifactsSupportingScalaVersions: String = (for (
    (artifactVersion, scalaSupport) <-
      scalaSupportByLatestSupportingArtifactVersion
  ) yield s"$artifactVersion (${scalaSupport.summary})").mkString(", ")
}

object ArtifactScalaVersionSupport {
  def forSpecifiedArtifactAndTargetType(
      allAvailableReleases: Seq[Release],
      specificArtifact: String,
      specificTargetType: ScalaTargetType
  ): ArtifactScalaVersionSupport = {

    val scalaTargetsByArtifactVersion = (for {
      release <- allAvailableReleases if release.isValid
      ref = release.reference if ref.artifact == specificArtifact
      scalaTarget <- ref.target if scalaTarget.targetType == specificTargetType
    } yield ref.version -> scalaTarget).groupMap(_._1)(_._2)

    ArtifactScalaVersionSupport(scalaTargetsByArtifactVersion)
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
