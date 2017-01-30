package ch.epfl.scala.index.model

import misc.{GithubInfo, GithubRepo}

/**
  * Project representation which contains all necessary meta data to
  * the project. Equivalent to a github repository or a sbt build.
  *
  * @param organization (ex: typelevel)
  * @param repository (ex: spark)
  * @param github github information representation
  * @param keywords predefined keywords (ex: database)
  * @param defaultArtifact when we land on a project page (ex: typelevel/cats) specify an artifact to select by default
  * @param defaultStableVersion when selecting a default version avoid preReleases if possible (otherwise select latest version)
  * @param artifacts names for this project (ex: cats-core, cats-free, ...)
  * @param releaseCount how many distinct versions we can find
  * @param customScalaDoc expression to substitute scaladoc
  * @param documentationLinks user documentation & etc
  * @param logoImageUrl absolute url to a logo (ex: http://spark.apache.org/images/spark-logo-trademark.png)
  * @param liveData the project was updated/created by a user
  * @param id elasticsearch id only used for updating projects
  * @param created date of the first release
  * @param updated date of the last release
  * @param targets (ex: scala_2.11, scala_2.12, scala-js_0.5)
  * @param dependencies to aggregate most depended upon libs (ex: spark, play framework, ...)
  * @param dependentCount Number of artifacts that depends on at least one release of at least one artifact of this project
  */
case class Project(
    organization: String,
    repository: String,
    github: Option[GithubInfo] = None,
    keywords: Set[String] = Set(),
    defaultStableVersion: Boolean = true,
    defaultArtifact: Option[String],
    artifacts: List[String],
    releaseCount: Int,
    customScalaDoc: Option[String] = None,
    documentationLinks: List[(String, String)] = List(),
    deprecated: Boolean = false,
    contributorsWanted: Boolean = false,
    artifactDeprecations: Set[String] = Set(),
    cliArtifacts: Set[String] = Set(),
    hasCli: Boolean = false, // HACK This field exists only for the purpose of being indexed by ES but his value must be equal to “cliArtifacts.nonEmpty”
    liveData: Boolean = false,
    id: Option[String] = None,
    created: Option[String],
    updated: Option[String],
    targetType: Set[String],
    scalaVersion: Set[String],
    scalaJsVersion: Set[String],
    scalaNativeVersion: Set[String],
    dependencies: Set[String],
    dependentCount: Int
) {

  def reference = Project.Reference(organization, repository)

  /**
    * create github representation for the repository
    *
    * @return
    */
  def githubRepo = GithubRepo(reference.organization, reference.repository)
}

object Project {

  /**
    * Simplified project reference name ex(typelevel/cats)
    *
    * @param organization github organization. ex: typelevel, akka, etc
    * @param repository github repository. ex: cats, akka, etc
    */
  case class Reference(organization: String, repository: String)
}
