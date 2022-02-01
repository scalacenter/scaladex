package scaladex.server

import scala.collection.immutable.SortedMap

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Platform
import scaladex.core.model.ScalaVersion
import scaladex.core.model.SemanticVersion

object BadgesSupport {
  def summaryOfLatestVersions(
      allAvailableArtifacts: Seq[Artifact],
      specificArtifact: Artifact.Name,
      specificTargetType: Platform.PlatformType
  ): String = {
    val summary = (for {
      artifact <- allAvailableArtifacts if artifact.isValid
      version = artifact.version if artifact.artifactName == specificArtifact
      scalaTarget = artifact.platform if artifact.platform.platformType == specificTargetType
    } yield version -> scalaTarget)
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.toSet)
      .toMap
    summaryOfLatestVersions(summary, specificTargetType)
  }

  def summaryOfLatestVersions(
      scalaTargetsByArtifactVersion: Map[SemanticVersion, Set[Platform]],
      targetType: Platform.PlatformType
  ): String = {
    // Are ALL `ScalaTarget`s instances of ScalaTargetWithPlatformBinaryVersion with targetTypes for platforms that
    // *fully dictate the Scala version used*?  If so, we want to summarise by `PlatformEdition`, rather than `LanguageVersion`
    val k = scalaTargetsByArtifactVersion
      .map { case (semantic, platforms) => platforms.map(semantic -> _) }
      .toSet
      .flatten
      .foldLeft(
        Option(
          Set.empty[(SemanticVersion, Platform)]
        )
      ) {
        case (
              Some(platformTargets),
              (
                artifactVersion,
                platformTarget
              )
            ) if platformTarget.isSbt =>
          Some(platformTargets + (artifactVersion -> platformTarget))
        case _ => None
      }
      .map(_.groupMap(_._1)(_._2))

    k.fold {
      SummariseLanguageVersions
        .summarise(scalaTargetsByArtifactVersion, targetType)
    }(a => SummarisePlatformEditions.summarise(a, targetType))
  }

  def summarisePlatformTargets(shortName: String, platformEditions: Set[BinaryVersion]): String = {
    val platformBinaryVersionsByTargetType =
      platformEditions.toSeq.sorted(
        BinaryVersion.ordering.reverse
      ) // remove .get

    s"$shortName ${platformBinaryVersionsByTargetType.mkString(", ")}"
  }

  /**
   * Implementations of this strategy will summarise different
   * properties of ScalaTargets - eg, the Scala LanguageVersion, or
   * the PlatformEdition.
   *
   * @tparam V the version type to be summarised by this SummaryStrategy
   */
  trait SummaryStrategy[T <: Platform, V] {

    /**
     * @return the pertinent (for this summary strategy) version of the supplied ScalaTarget
     */
    def versionFor(t: T): V

    def notableSupportByArtifactVersion(
        scalaTargetsByArtifactVersion: Map[SemanticVersion, Set[T]]
    ): Map[SemanticVersion, Set[V]] = {
      val keyVersionsByArtifactVersion: Map[SemanticVersion, Set[V]] =
        scalaTargetsByArtifactVersion.view.mapValues(_.map(versionFor)).toMap
      val interestingVersions: Set[V] =
        keyVersionsByArtifactVersion.values.flatten.toSet

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
        scalaTargetsByArtifactVersion: Map[SemanticVersion, Set[T]],
        platformType: Platform.PlatformType
    ): String =
      (for {
        (artifactVersion, keyVersions) <-
          SortedMap.from(
            notableSupportByArtifactVersion(scalaTargetsByArtifactVersion)
          )(SemanticVersion.ordering.reverse)
      } yield s"$artifactVersion (${summarise(scalaTargetsByArtifactVersion(artifactVersion), keyVersions, platformType)})")
        .mkString(", ")

    def summarise(
        scalaTargets: Set[T],
        interestingKeyVersions: Set[V],
        platformType: Platform.PlatformType
    ): String
  }

  /**
   * Summarises primarily the available Scala LanguageVersions - but
   * will secondarily summarise the PlatformEditions that are supported
   * for all those Scala LanguageVersions.
   */
  object SummariseLanguageVersions extends SummaryStrategy[Platform, ScalaVersion] {
    override def versionFor(t: Platform): ScalaVersion =
      t.scalaVersion.get // todo change

    override def summarise(
        platforms: Set[Platform],
        interestingKeyVersions: Set[ScalaVersion],
        platformType: Platform.PlatformType
    ): String = {
      val scalaTargetsByLanguageVersion: Map[ScalaVersion, Set[Platform]] =
        platforms
          .groupBy(_.scalaVersion.get)
          .view
          .filterKeys(interestingKeyVersions)
          .toMap

      val languageVersions: Seq[ScalaVersion] =
        scalaTargetsByLanguageVersion.keys.toSeq.sorted(ScalaVersion.ordering.reverse)

      val platformEditionsSupportedForAllLanguageVersions: Set[BinaryVersion] =
        scalaTargetsByLanguageVersion.values
          .map(_.flatMap(_.platformVersion))
          .reduce(_.intersect(_))

      val targetsSummary: Option[String] =
        Option.when(platformEditionsSupportedForAllLanguageVersions.nonEmpty)(
          " - " + summarisePlatformTargets(
            platformType.toString,
            platformEditionsSupportedForAllLanguageVersions
          )
        )

      s"Scala ${languageVersions.map(_.encode).mkString(", ")}${targetsSummary.mkString}"
    }
  }

  /**
   * Summarises *just* PlatformEdition versions - it's only appropriate
   * if *all* the ScalaTargets are targets with PlatformEdition types
   * where the platform version *fully dictates* the Scala version -
   * like sbt.
   */
  object SummarisePlatformEditions extends SummaryStrategy[Platform, BinaryVersion] {
    override def versionFor(t: Platform): BinaryVersion = t.platformVersion.get
    override def summarise(
        scalaTargets: Set[Platform],
        interestingKeyVersions: Set[BinaryVersion],
        platformType: Platform.PlatformType
    ): String =
      summarisePlatformTargets("sbt", interestingKeyVersions)
  }
}
