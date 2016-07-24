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
  * @param artifacts names for this project (ex: cats-core, cats-free, ...)
  * @param releaseCount how many distinct versions we can find
  * @param customScalaDocUrl expression to subsitute scaladoc
  * @param documentationLinks user documentation & etc
  * @param logoImageUrl absolute url to a logo (ex: http://spark.apache.org/images/spark-logo-trademark.png)
  * @param background background css attributes (see https://developer.mozilla.org/en/docs/Web/CSS/background)
  * @param foregroundColor color css attribute (see https://developer.mozilla.org/en-US/docs/Web/CSS/color)
  * @param liveData the project was updated/created by a user
  * @param id elasticsearch id only used for updating projects
  * @param created date of the first release
  * @param updated date of the last release
  * @param targets (ex: scala_2.11, scala_2.12, scala-js_0.5)
  * @param dependencies to aggregate most dependended uppon libs (ex: spark, playframework, ...)
  */
case class Project(
    organization: String,
    repository: String,
    github: Option[GithubInfo] = None,
    keywords: Set[String] = Set(),
    defaultArtifact: Option[String] = None,
    artifacts: List[String] = Nil,
    releaseCount: Int = 0,
    customScalaDoc: Option[String] = None,
    documentationLinks: List[String] = List(),
    deprecated: Boolean = false,
    contributorsWanted: Boolean = false,
    artifactDeprecations: Set[String] = Set(),
    logoImageUrl: Option[String] = None,
    background: Option[String] = None,
    foregroundColor: Option[String] = None,
    liveData: Boolean = false,
    id: Option[String] = None,
    created: Option[String] = None,
    updated: Option[String] = None,
    targets: List[String] = Nil,
    dependencies: List[String] = Nil
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
