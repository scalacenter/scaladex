package ch.epfl.scala.index.model

import misc.{GithubInfo, GithubRepo, Url}

/**
  * Project representation which contains all necessary meta data to
  * the project. Equivalent to a github repository or a sbt build.
  *
  * @param organization (ex: typelevel)
  * @param repository (ex: spark)
  * @param github github information representation
  * @param keywords predefined keywords (ex: database)
  * @param stackOverflowTags see http://stackoverflow.com/tags (ex: akka)
  * @param defaultArtifact when we land on a project page (ex: typelevel/cats) specify an artifact to select by default
  * @param twitter @ handle (ex: @akkateam, @typelevel)
  * @param parentOrganization agglomerate of github organizations: lightbend(akka, play, ...), verizon(verizon, oncue), etc
  * @param logoImageUrl absolute url to a logo (ex: http://spark.apache.org/images/spark-logo-trademark.png)
  * @param _id elasticsearch id only used for updating projects
  * @param created date of the first release
  * @param updated date of the last release
  * @param targets (ex: scala_2.11, scala_2.12, scala-js_0.5)
  * @param dependencies to aggregate most dependended uppon libs (ex: spark, playframework, ...)
  */
case class Project(
    organization: String,
    repository: String,
    github: Option[GithubInfo] = None,
    keywords: List[String] = Nil,
    stackOverflowTags: List[String] = Nil,
    defaultArtifact: Option[String] = None,
    twitter: Option[String] = None,
    parentOrganization: Option[String] = None,
    logoImageUrl: Option[Url] = None,
    _id: Option[String] = None,
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
