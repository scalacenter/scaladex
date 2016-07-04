package ch.epfl.scala.index.model

import misc.{GeneralReference, ISO_8601_Date, MavenReference}
import release._

/**
 * Artifact release representation
 * @param maven famous maven triple: org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
 * @param reference similar to maven but with a clean artifact name
 * @param name human readable name (ex: Apache Spark)
 * @param description the description of that release
 * @param releaseDates potentially various dates because bintray allows republishing
 * @param mavenCentral availability on the central repository
 * @param licenses a bunch of licences
 * @param scalaDependencies bunch of scala dependencies
 * @param javaDependencies bunch of java dependencies
 * @param reverseDependencies bunch of reversed dependencies
 */
case class Release(
  maven: MavenReference,
  reference: Release.Reference,
  name: Option[String] = None,
  description: Option[String] = None,
  releaseDates: List[ISO_8601_Date] = Nil,
  mavenCentral: Boolean = false,
  licenses: Set[License] = Set(),
  nonStandardLib: Boolean = false,

  /* split dependencies in 2 fields because elastic can't handle 2 different types
   * in one field. That is a simple workaround for that
   */
  scalaDependencies: Seq[ScalaDependency] = Seq(),
  javaDependencies: Seq[JavaDependency] = Seq(),
  reverseDependencies: Seq[ScalaDependency] = Seq()
) {

  /**
   * string representation for sbt dependency
   * @return
   */
  def sbtInstall = {

    val scalaJs = reference.target.scalaJsVersion.isDefined
    val crossFull = reference.target.scalaVersion.patch.isDefined

    val (artifactOperator, crossSuffix) =
      if (nonStandardLib) ("%", "")
      else if (scalaJs) ("%%%", "")
      else if (crossFull) ("%", " cross CrossVersion.full")
      else ("%%", "")

    s""""${maven.groupId}" $artifactOperator "${reference.artifact}" % "${reference.version}$crossSuffix""""
  }

  /**
   * string representation for maven dependency
   * @return
   */
  def mavenInstall = {
    import maven._
    s"""|<dependency>
        |  <groupId>$groupId</groupId>
        |  <artifactId>$artifactId</artifactId>
        |  <version>$version</version>
        |</dependency>""".stripMargin
  }

  /**
   * string representation for gradle dependency
   * @return
   */
  def gradleInstall = {
    import maven._
    s"compile group: '$groupId', name: '$artifactId', version: '$version'"
  }

  /**
   * Url to the scala-docs.
   * @return
   */
  def scalaDocURI: Option[String] = {
    if (mavenCentral) {

      import maven._

      /* no frame
       * hosted on s3 at:
       *https://static.javadoc.io/$groupId/$artifactId/$version/index.html#package
       * HEAD to check 403 vs 200
       */
      Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
    } else None
  }

  /**
   * ordered scala dependencies - tests last
   */
  lazy val orderedDependencies = {
    val (a, b) = scalaDependencies.sortBy(_.reference.name).partition(_.scope.contains("test"))
    b.groupBy(b => b).values.flatten.toList ++ a
  }

  /**
   * ordered java dependencies - tests last
   * - watch out the performance on /scala/scala-library
   */
  lazy val orderedJavaDependencies = {

    val (a, b) = javaDependencies.sortBy(_.reference.name).partition(_.scope.contains("test"))
    b.groupBy(b => b).values.flatten.toList ++ a
  }

  /**
   * ordered reverse scala dependencies - tests last
   * - watch out the performance on /scala/scala-library
   */
  lazy val orderedReverseDependencies = {

    val (a, b) = reverseDependencies.partition(_.scope.contains("test"))
    b.groupBy(b => b).values.flatten.toList ++ a
  }

  /** collect all unique organization/artifact dependency
   * - watch out the performance on /scala/scala-library
   */
  lazy val uniqueOrderedReverseDependencies: Seq[ScalaDependency] = {

    orderedReverseDependencies.groupBy(_.reference.name).values.map(_.head).toSeq.sortBy(_.reference.name)
  }

  /**
   * number of dependencies (java + scala)
   */
  lazy val dependencyCount = scalaDependencies.size + javaDependencies.size

  /**
   * collect a list of version for a reverse dependency
   * - watch out the performance on /scala/scala-library
   *
   * @param dep current looking dependency
   * @return
   */
  def versionsForReverseDependencies(dep: ScalaDependency): Seq[SemanticVersion] = {

    orderedReverseDependencies.filter(d => d.reference.name == dep.reference.name).map(_.reference.version)
  }
}


object Release {

  /**
   * Release Reference representation
   * @param organization the organisation name like    typelevel | akka
   * @param artifact the artifact name like            cats-core | akka-http-experimental
   * @param version the semantic version like              0.6.0 | 0.6.0-RC1
   * @param target the target this reference can run on
   */
  case class Reference(
    organization: String,
    artifact: String,
    version: SemanticVersion,
    target: ScalaTarget
  ) extends GeneralReference {

    /**
     * concated name of organisation/artifact
     * @return
     */
    def name: String = s"$organization/$artifact"

    /**
     * the http reference on index.scala-lang.org
     * @return
     */
    def httpUrl: String = s"/$organization/$artifact/$version"
  }

}