package ch.epfl.scala.index.model

import release._

/**
 * Artifact release representation
 * @param maven famous maven triple: org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
 * @param reference similar to maven but with a clean artifact name
 * @param name human readable name (ex: Apache Spark)
 * @param resolver if not on maven central (ex: Bintray)
 * @param description the description of that release
 * @param released first release date
 * @param licenses a bunch of licences
 * @param isNonStandardLib if not using artifactName_scalaVersion convention
 * @param id Elastic search id
 * @param scalaDependencies bunch of scala dependencies
 * @param javaDependencies bunch of java dependencies
 * @param reverseDependencies bunch of reversed dependencies
 */
case class Release(
    maven: MavenReference,
    reference: Release.Reference,
    resolver: Option[Resolver],
    name: Option[String],
    description: Option[String],
    released: Option[String],
    licenses: Set[License],
    isNonStandardLib: Boolean,
    id: Option[String],
    liveData: Boolean,
    /* split dependencies in 2 fields because elastic can't handle 2 different types
     * in one field. That is a simple workaround for that
     */
    scalaDependencies: Seq[ScalaDependency],
    javaDependencies: Seq[JavaDependency],
    reverseDependencies: Seq[ScalaDependency],
    internalDependencies: Seq[ScalaDependency],
    // this part for elasticsearch search
    targetType: String, // JVM, JS, Native, JAVA, SBT
    fullScalaVersion: Option[String],
    scalaVersion: Option[String],
    scalaJsVersion: Option[String],
    scalaNativeVersion: Option[String],
    sbtVersion: Option[String]
) {

  /**
   * string representation for sbt dependency
   * @return
   */
  def sbtInstall: String = {
    val isSbtPlugin = reference.target.flatMap(_.sbtVersion).isDefined

    val install =
      if (!isSbtPlugin) {
        val isScalaJs =
          reference.target.flatMap(_.scalaJsVersion).isDefined

        val isScalaNative =
          reference.target.flatMap(_.scalaNativeVersion).isDefined

        val isCrossFull =
          reference.target.flatMap(_.scalaVersion.patch).isDefined

        val isPreRelease =
          reference.target.flatMap(_.scalaVersion.preRelease).isDefined

        val (artifactOperator, crossSuffix) =
          if (isNonStandardLib) ("%", "")
          else if (isScalaJs || isScalaNative) ("%%%", "")
          else if (isCrossFull & !isPreRelease)
            ("%", " cross CrossVersion.full")
          else ("%%", "")

        s"""libraryDependencies += "${maven.groupId}" $artifactOperator "${reference.artifact}" % "${reference.version}"$crossSuffix"""
      } else {
        s"""addSbtPlugin("${maven.groupId}" % "${reference.artifact}" % "${reference.version}")"""
      }

    List(
      Some(install),
      resolver.flatMap(_.sbt.map("resolvers += " + _))
    ).flatten.mkString(System.lineSeparator)
  }

  /**
   * string representation for Ammonite loading
   * @return
   */
  def ammInstall = {

    def addResolver(r: Resolver) =
      s"""|import ammonite._, Resolvers._
          |val res = Resolver.Http(
          |  "${r.name}",
          |  "${r.url}",
          |  IvyPattern,
          |  false)
          |interp.resolvers() = interp.resolvers() :+ res""".stripMargin

    val artifactOperator = if (isNonStandardLib) ":" else "::"

    List(
      Some(
        s"import $$ivy.`${maven.groupId}$artifactOperator${reference.artifact}:${reference.version}`"
      ),
      resolver.map(addResolver)
    ).flatten.mkString(System.lineSeparator)
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
  def scalaDocURL(customScalaDoc: Option[String]): Option[String] = {
    customScalaDoc match {
      case None =>
        if (resolver.isEmpty) {
          import maven._
          /* no frame
           * hosted on s3 at:
           *https://static.javadoc.io/$groupId/$artifactId/$version/index.html#package
           * HEAD to check 403 vs 200
           */
          Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
        } else None
      case Some(rawLink) => Some(evalLink(rawLink))
    }
  }

  def documentationURLs(
      documentationLinks: List[(String, String)]
  ): List[(String, String)] = {
    documentationLinks.map { case (label, url) => (label, evalLink(url)) }
  }

  /** Documentation link are often related to a release version
   * for example: https://playframework.com/documentation/2.6.x/Home
   * we want to substitute input such as
   * https://playframework.com/documentation/[major].[minor].x/Home
   */
  private def evalLink(rawLink: String): String = {
    rawLink
      .replaceAllLiterally("[groupId]", maven.groupId.toString)
      .replaceAllLiterally("[artifactId]", maven.artifactId.toString)
      .replaceAllLiterally("[version]", reference.version.toString)
      .replaceAllLiterally("[major]", reference.version.major.toString)
      .replaceAllLiterally("[minor]", reference.version.minor.toString)
      .replaceAllLiterally("[name]", reference.artifact)
  }

  /**
   * ordered scala dependencies - tests last
   */
  lazy val orderedDependencies = {
    val (a, b) = scalaDependencies
      .sortBy(_.reference.name)
      .partition(_.scope.contains("test"))
    b.groupBy(b => b).values.flatten.toList ++ a
  }

  /**
   * ordered java dependencies - tests last
   * - watch out the performance on /scala/scala-library
   */
  lazy val orderedJavaDependencies = {

    val (a, b) = javaDependencies
      .sortBy(_.reference.name)
      .partition(_.scope.contains("test"))
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

    orderedReverseDependencies
      .groupBy(_.reference.name)
      .values
      .map(_.head)
      .toSeq
      .sortBy(_.reference.name)
  }

  /**
   * number of dependencies (java + scala)
   */
  lazy val dependencyCount = scalaDependencies.size + javaDependencies.size

  /**
   * number of internal dependencies
   */
  lazy val internalDependencyCount = internalDependencies.size

  /**
   * collect a list of version for a reverse dependency
   * - watch out the performance on /scala/scala-library
   *
   * @param dep current looking dependency
   * @return
   */
  def versionsForReverseDependencies(
      dep: ScalaDependency
  ): Seq[SemanticVersion] = {

    orderedReverseDependencies
      .filter(d => d.reference.name == dep.reference.name)
      .map(_.reference.version)
  }

  def isScalaLib: Boolean = reference.isScalaLib
}

object Release {

  /**
   * @param organization (ex: typelevel | akka)
   * @param repository (ex: cats | akka)
   * @param artifact (ex: cats-core | akka-http-experimental)
   */
  case class Reference(
      organization: String,
      repository: String,
      artifact: String,
      version: SemanticVersion,
      target: Option[ScalaTarget]
  ) extends GeneralReference {

    def projectReference = Project.Reference(organization, repository)
    def name = s"$organization/$artifact"
    def httpUrl = {
      val targetQuery = target.map(t => s"?target=${t.encode}").getOrElse("")

      s"/$organization/$repository/$artifact/$version$targetQuery"
    }

    def isScalaLib: Boolean = {
      organization == "scala" &&
      repository == "scala" &&
      artifact == "scala-library"
    }

  }
}
