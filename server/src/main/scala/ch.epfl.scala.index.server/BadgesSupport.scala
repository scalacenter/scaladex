package ch.epfl.scala.index.server

import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.model.{Release, SemanticVersion}

import scala.collection.immutable.{SortedMap, SortedSet}

object BadgesSupport {
  def summaryOfLatestVersions(
      allAvailableReleases: Seq[Release],
      specificArtifact: String,
      specificTargetType: ScalaTargetType
  ): String = summaryOfLatestVersions(
    (for {
      release <- allAvailableReleases if release.isValid
      ref = release.reference if ref.artifact == specificArtifact
      scalaTarget <- ref.target if scalaTarget.targetType == specificTargetType
    } yield ref.version -> scalaTarget)
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.toSet)
      .toMap
  )

  def summaryOfLatestVersions(
      scalaTargetsByArtifactVersion: Map[SemanticVersion, Set[ScalaTarget]]
  ): String = {
    // Are ALL `ScalaTarget`s instances of ScalaTargetWithPlatformBinaryVersion withe targetTypes for platforms that
    // *fully dictate the Scala version used*?  If so, we want to summarise by `PlatformEdition`, rather than `LanguageVersion`
    val platformTargetsByArtifactVersion: Option[
      Map[SemanticVersion, Set[ScalaTargetWithPlatformBinaryVersion]]
    ] = (for {
      (artifactVersion, scalaTargets) <- scalaTargetsByArtifactVersion.toSeq
      scalaTarget <- scalaTargets
    } yield artifactVersion -> scalaTarget)
      .foldLeft(
        Option(
          Set.empty[(SemanticVersion, ScalaTargetWithPlatformBinaryVersion)]
        )
      ) {
        case (
              Some(platformTargets),
              (
                artifactVersion,
                platformTarget: ScalaTargetWithPlatformBinaryVersion
              )
            ) if platformTarget.targetType.platformVersionDeterminesScalaVersion =>
          Some(platformTargets + (artifactVersion -> platformTarget))
        case _ => None
      }
      .map(_.groupMap(_._1)(_._2))

    platformTargetsByArtifactVersion.fold(
      SummariseLanguageVersions.summarise(scalaTargetsByArtifactVersion)
    )(
      SummarisePlatformEditions.summarise
    )
  }

  def summarisePlatformTargets(
      platformEditions: Set[PlatformEdition],
      sep: String
  ): String = {
    val platformBinaryVersionsByTargetType = SortedMap.from(
      platformEditions
        .groupMap(_.targetType.shortName)(_.version)
        .view
        .mapValues(
          SortedSet.from(_)(BinaryVersion.ordering.reverse)
        )
    )

    platformBinaryVersionsByTargetType
      .map { case (shortName, platformBinaryVersions) =>
        s"$shortName ${platformBinaryVersions.mkString(sep)}"
      }
      .mkString(", ")
  }

  /**
   * Implementations of this strategy will summarise different
   * properties of ScalaTargets - eg, the Scala LanguageVersion, or
   * the PlatformEdition.
   *
   * @tparam V the version type to be summarised by this SummaryStrategy
   */
  trait SummaryStrategy[T <: ScalaTarget, V] {

    /**
     * @return the pertinent (for this summary strategy) version of the supplied ScalaTarget
     */
    def versionFor(t: T): V
    def removeSuperfluousVersionsFrom(versions: Set[V]): Set[V]

    def notableSupportByArtifactVersion(
        scalaTargetsByArtifactVersion: Map[SemanticVersion, Set[T]]
    ): Map[SemanticVersion, Set[V]] = {
      val keyVersionsByArtifactVersion: Map[SemanticVersion, Set[V]] =
        scalaTargetsByArtifactVersion.view.mapValues(_.map(versionFor)).toMap
      val interestingVersions: Set[V] = {
        val allVersions = keyVersionsByArtifactVersion.values.flatten.toSet
        removeSuperfluousVersionsFrom(allVersions)
      }

      val latestArtifactForKeyVersion: Map[V, SemanticVersion] = (for {
        (artifactVersion, keyVersions) <- keyVersionsByArtifactVersion.toSeq
        interestingKeyVersion <- keyVersions.filter(interestingVersions)
      } yield interestingKeyVersion -> artifactVersion)
        .groupMap(_._1)(_._2)
        .view
        .mapValues(_.max)
        .toMap

      latestArtifactForKeyVersion
        .groupMap(_._2)(_._1)
        .view
        .mapValues(_.toSet)
        .toMap
    }

    def summarise(
        scalaTargetsByArtifactVersion: Map[SemanticVersion, Set[T]]
    ): String = (for {
      (artifactVersion, keyVersions) <-
        SortedMap.from(
          notableSupportByArtifactVersion(scalaTargetsByArtifactVersion)
        )(SemanticVersion.ordering.reverse)
    } yield s"$artifactVersion (${summarise(scalaTargetsByArtifactVersion(artifactVersion), keyVersions)})")
      .mkString(", ")

    def summarise(scalaTargets: Set[T], interestingKeyVersions: Set[V]): String
  }

  /**
   * Summarises primarily the available Scala LanguageVersions - but
   * will secondarily summarise the PlatformEditions that are supported
   * for all those Scala LanguageVersions.
   */
  object SummariseLanguageVersions
      extends SummaryStrategy[ScalaTarget, LanguageVersion] {
    override def versionFor(t: ScalaTarget): LanguageVersion = t.languageVersion
    override def removeSuperfluousVersionsFrom(
        versions: Set[LanguageVersion]
    ): Set[
      LanguageVersion
    ] = // we're only interested in the latest version of a binary family
      versions.groupBy(_.family).values.map(_.max).toSet

    override def summarise(
        scalaTargets: Set[ScalaTarget],
        interestingKeyVersions: Set[LanguageVersion]
    ): String = {
      val scalaTargetsByLanguageVersion
          : Map[LanguageVersion, Set[ScalaTarget]] =
        scalaTargets
          .groupBy(_.languageVersion)
          .view
          .filterKeys(interestingKeyVersions)
          .toMap

      val languageVersions: SortedSet[LanguageVersion] =
        SortedSet.from(scalaTargetsByLanguageVersion.keySet)(
          LanguageVersion.ordering.reverse
        )

      val platformEditionsSupportedForAllLanguageVersions
          : Set[PlatformEdition] =
        scalaTargetsByLanguageVersion.values
          .map(_.collect { case st: ScalaTargetWithPlatformBinaryVersion =>
            st.platformEdition
          })
          .reduce(_ & _)

      val targetsSummary: Option[String] =
        Option.when(platformEditionsSupportedForAllLanguageVersions.nonEmpty)(
          " - " + summarisePlatformTargets(
            platformEditionsSupportedForAllLanguageVersions,
            "+"
          )
        )

      s"Scala ${languageVersions.mkString(", ")}${targetsSummary.mkString}"
    }
  }

  /**
   * Summarises *just* PlatformEdition versions - it's only appropriate
   * if *all* the ScalaTargets are targets with PlatformEdition types
   * where the platform version *fully dictates* the Scala version -
   * like sbt.
   */
  object SummarisePlatformEditions
      extends SummaryStrategy[
        ScalaTargetWithPlatformBinaryVersion,
        PlatformEdition
      ] {
    override def versionFor(
        t: ScalaTargetWithPlatformBinaryVersion
    ): PlatformEdition = t.platformEdition
    override def removeSuperfluousVersionsFrom(
        versions: Set[PlatformEdition]
    ): Set[PlatformEdition] = versions
    override def summarise(
        scalaTargets: Set[ScalaTargetWithPlatformBinaryVersion],
        interestingKeyVersions: Set[PlatformEdition]
    ): String =
      summarisePlatformTargets(
        scalaTargets.map(_.platformEdition).filter(interestingKeyVersions),
        ", "
      )
  }
}
