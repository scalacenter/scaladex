package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.misc.{GithubInfo, GithubRepo, Url}

/**
 * Project representation which contains all necessary meta data to
 * the project.
 *
 * @param reference simplified reference name ex(typelevel/cats-core)
 * @param artifacts the artifact representation itself
 * @param github github information representation
 * @param keywords predefined keywords (ex: database)
 * @param stackOverflowTags http://stackoverflow.com/tags
 * @param twitter @ handle (ex: @akkateam, @typelevel)
 * @param parentOrganization agglomerate of github organizations: lightbend(akka, play, ...), verizon(verizon, oncue), etc
 * @param logoImageUrl absolute url to a logo (ex: http://spark.apache.org/images/spark-logo-trademark.png)
 * @param _id elastic search id
 * @param created created date representation
 * @param lastUpdate last update representation
 * @param support elastic support tags for ex(scala_2.11, scala_2.12, spark, scala-js_0.5)
 */
case class Project(
  reference: Project.Reference,
  artifacts: List[Artifact],
  github: Option[GithubInfo] = None,
  keywords: List[String] = Nil,
  stackOverflowTags: List[String] = Nil,
  twitter: Option[String] = None,
  parentOrganization: Option[String] = None,
  logoImageUrl: Option[Url] = None,
  _id: Option[Int] = None,
  created: Option[String] = None,
  lastUpdate: Option[String] = None,
  support: List[String] = Nil
) {

  /**
   * create github representation for the repository
   *
   * @return
   */
  def githubRepo = GithubRepo(reference.organization, reference.repository)
}

object Project{

  /**
   * Simplified project reference name ex(typelevel/cats-core)
   *
   * @param organization github organization. ex: typelevel, akka, etc
   * @param repository github repository. ex: cats, akka, etc
   */
  case class Reference(organization: String, repository: String)
}