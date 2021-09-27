package ch.epfl.scala.index.server

import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.newModel.NewRelease

object BadgesSupport {
  def summaryOfLatestVersions(
      allAvailableReleases: Seq[NewRelease],
      specificArtifact: NewRelease.ArtifactName,
      specificTargetType: ScalaTargetType
  ): String = summaryOfLatestVersions(
    (for {
      release <- allAvailableReleases if release.isValid
      version = release.version if release.artifactName == specificArtifact
      scalaTarget = release.platform if release.targetType == specificTargetType
    } yield version -> scalaTarget)
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.toSet)
      .toMap
  )

  def summaryOfLatestVersions(
      scalaTargetsByArtifactVersion: Map[SemanticVersion, Set[Platform]]
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
          println(s"platformTarget = ${platformTarget}")
          Some(platformTargets + (artifactVersion -> platformTarget))
        case _ => None
      }
      .map(_.groupMap(_._1)(_._2))

    k.fold({
      println(s"SummariseLanguageVersions $scalaTargetsByArtifactVersion")
      SummariseLanguageVersions.summarise(scalaTargetsByArtifactVersion)
    })(a => {
      println(s"summarize platform edition $a")
      SummarisePlatformEditions.summarise(a)
    })
  }

  def summarisePlatformTargets(
      platformEditions: Set[Platform]
  ): String = {
    val platformBinaryVersionsByTargetType = SortedMap.from(
      platformEditions
        .groupMap(_.shortName)(_.platformVersion.get) // remove .get
        .view
        .mapValues(
          SortedSet.from(_)(BinaryVersion.ordering.reverse)
        )
    )

    platformBinaryVersionsByTargetType
      .map { case (shortName, platformBinaryVersions) =>
        s"$shortName ${platformBinaryVersions.mkString(", ")}"
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
  trait SummaryStrategy[T <: Platform, V] {

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
    ): String = {
      val k = (for {
        (artifactVersion, keyVersions) <-
          SortedMap.from(
            notableSupportByArtifactVersion(scalaTargetsByArtifactVersion)
          )(SemanticVersion.ordering.reverse)
      } yield s"$artifactVersion (${summarise(scalaTargetsByArtifactVersion(artifactVersion), keyVersions)})")
        .mkString(", ")
      println(s"k = ${k}")
      k
    }

    def summarise(scalaTargets: Set[T], interestingKeyVersions: Set[V]): String
  }

  /**
   * Summarises primarily the available Scala LanguageVersions - but
   * will secondarily summarise the PlatformEditions that are supported
   * for all those Scala LanguageVersions.
   */
  object SummariseLanguageVersions
      extends SummaryStrategy[Platform, ScalaLanguageVersion] {
    override def versionFor(t: Platform): ScalaLanguageVersion =
      t.scalaVersion.get //todo change
    override def removeSuperfluousVersionsFrom(
        versions: Set[ScalaLanguageVersion]
    ): Set[
      ScalaLanguageVersion
    ] = // we're only interested in the latest version of a binary family
      versions.groupBy(_.family).values.map(_.max).toSet

    override def summarise(
        platforms: Set[Platform],
        interestingKeyVersions: Set[ScalaLanguageVersion]
    ): String = {
      val scalaTargetsByLanguageVersion
          : Map[ScalaLanguageVersion, Set[Platform]] =
        platforms
          .groupBy(_.scalaVersion.get)
          .view
          .filterKeys(interestingKeyVersions)
          .toMap

      val languageVersions: SortedSet[ScalaLanguageVersion] =
        SortedSet.from(scalaTargetsByLanguageVersion.keySet)(
          ScalaLanguageVersion.ordering.reverse
        )

      // this code aimes to verify that we have the same platform version for each scala version
      // needs to  be improved
      val platformEditionsSupportedForAllLanguageVersions: Set[Platform] = {
        val platformBinaryVersion = scalaTargetsByLanguageVersion.values.toSeq
          .map(platforms => platforms.flatMap(_.platformVersion))
          .toSet
        println(s"platformBinaryVersion = ${platformBinaryVersion}")
        if (
          platformBinaryVersion.size == 1 && platformBinaryVersion.head.nonEmpty
        )
          scalaTargetsByLanguageVersion.values.head
        else Set()
      }

      println(
        s"platformEditionsSupportedForAllLanguageVersions = ${platformEditionsSupportedForAllLanguageVersions}"
      )

      val targetsSummary: Option[String] =
        Option.when(platformEditionsSupportedForAllLanguageVersions.nonEmpty)(
          " - " + summarisePlatformTargets(
            platformEditionsSupportedForAllLanguageVersions
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
        Platform,
        BinaryVersion
      ] {
    override def versionFor(
        t: Platform
    ): BinaryVersion = t.platformVersion.get
    override def removeSuperfluousVersionsFrom(
        versions: Set[BinaryVersion]
    ): Set[BinaryVersion] = versions // why not
    override def summarise(
        scalaTargets: Set[Platform],
        interestingKeyVersions: Set[BinaryVersion]
    ): String =
      summarisePlatformTargets(
        scalaTargets.filter(p =>
          interestingKeyVersions.contains(p.platformVersion.get)
        )
      )
  }
}
