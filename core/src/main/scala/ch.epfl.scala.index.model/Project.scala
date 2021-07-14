package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Js
import ch.epfl.scala.index.model.release.LanguageVersion
import ch.epfl.scala.index.model.release.Native
import ch.epfl.scala.index.model.release.Sbt

/**
 * Project representation which contains all necessary meta data to
 * the project. Equivalent to a github repository or a sbt build.
 *
 * @param organization (ex: typelevel)
 * @param repository (ex: spark)
 * @param github github information representation
 * @param defaultStableVersion when selecting a default version avoid preReleases if possible (otherwise select latest version)
 * @param defaultArtifact when we land on a project page (ex: typelevel/cats) specify an artifact to select by default
 * @param strictVersions only show valid semantic version
 * @param artifacts names for this project (ex: cats-core, cats-free, ...)
 * @param releaseCount how many distinct versions we can find
 * @param customScalaDoc expression to substitute scaladoc
 * @param documentationLinks user documentation & etc
 * @param deprecated whether or not project is deprecated
 * @param contributorsWanted true if project is looking for new contributors
 * @param artifactDeprecations
 * @param cliArtifacts
 * @param hasCli true if project has cli
 * @param liveData the project was updated/created by a user
 * @param id elasticsearch id only used for updating projects
 * @param created date of the first release
 * @param updated date of the last release
 * @param targetType (ex: scala_2.11, scala_2.12, scala-js_0.5)
 * @param scalaVersion (ex: scala_2.11, scala_2.12)
 * @param scalaJsVersion (ex: scala-js_0.5)
 * @param scalaNativeVersion
 * @param dependencies to aggregate most depended upon libs (ex: spark, play framework, ...)
 * @param dependentCount Number of artifacts that depends on at least one release of at least one artifact of this project
 * @param primaryTopic most significative topic (ex: Circe: json)
 */
case class Project(
    organization: String,
    repository: String,
    github: Option[GithubInfo] = None,
    defaultStableVersion: Boolean = true,
    defaultArtifact: Option[String],
    strictVersions: Boolean = false,
    artifacts: List[String],
    releaseCount: Int,
    customScalaDoc: Option[String] = None,
    documentationLinks: List[(String, String)] = List(),
    deprecated: Boolean = false,
    contributorsWanted: Boolean = false,
    artifactDeprecations: Set[String] = Set(),
    cliArtifacts: Set[String] = Set(),
    hasCli: Boolean =
      false, // HACK This field exists only for the purpose of being indexed by ES but his value must be equal to “cliArtifacts.nonEmpty”
    liveData: Boolean = false,
    id: Option[String] = None,
    created: Option[String],
    updated: Option[String],
    targetType: List[String],
    scalaVersion: List[String],
    scalaJsVersion: List[String],
    scalaNativeVersion: List[String],
    sbtVersion: List[String],
    dependencies: Set[String],
    dependentCount: Int,
    primaryTopic: Option[String] = None
) {
  def formatForDisplaying: Project = {
    copy(
      id = None,
      scalaVersion = LanguageVersion.sortFamilies(scalaVersion),
      scalaJsVersion =
        BinaryVersion.sortAndFilter(scalaJsVersion, Js.isValid).toList,
      scalaNativeVersion = BinaryVersion
        .sortAndFilter(scalaNativeVersion, Native.isValid)
        .toList,
      sbtVersion = BinaryVersion.sortAndFilter(sbtVersion, Sbt.isValid).toList
    )
  }

  def reference: Project.Reference = Project.Reference(organization, repository)

  /**
   * create github representation for the repository
   *
   * @return
   */
  def githubRepo: GithubRepo = reference.githubRepo
}

object Project {

  /**
   * Simplified project reference name ex(typelevel/cats)
   *
   * @param organization github organization. ex: typelevel, akka, etc
   * @param repository github repository. ex: cats, akka, etc
   */
  case class Reference(organization: String, repository: String) {
    def githubRepo: GithubRepo = GithubRepo(organization, repository)
    override def toString: String = s"$organization/$repository"
  }
}
