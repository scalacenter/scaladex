package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.release._

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
 * @param javaDependencies bunch of java dependencies
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
    // only the java dependencies are stored in a release
    // a java dependency is dependency toward a release that is not indexed in Scaladex
    javaDependencies: Seq[JavaDependency],
    // TODO replace all fields below by ScalaTarget data type
    targetType: String, // JVM, JS, Native, JAVA, SBT
    scalaVersion: Option[String],
    scalaJsVersion: Option[String],
    scalaNativeVersion: Option[String],
    sbtVersion: Option[String]
) {

  def isValid: Boolean = {
    reference.isValid
  }

  /**
   * string representation for sbt dependency
   * @return
   */
  def sbtInstall: String = {
    val install = reference.target match {
      case Some(SbtPlugin(_, _)) =>
        s"""addSbtPlugin("${maven.groupId}" % "${reference.artifact}" % "${reference.version}")"""
      case _ if isNonStandardLib =>
        s"""libraryDependencies += "${maven.groupId}" % "${reference.artifact}" % "${reference.version}""""
      case Some(ScalaJs(_, _) | ScalaNative(_, _)) =>
        s"""libraryDependencies += "${maven.groupId}" %%% "${reference.artifact}" % "${reference.version}""""
      case Some(ScalaJvm(ScalaVersion(_: PatchBinary))) | Some(
            ScalaJvm(Scala3Version(_: PatchBinary))
          ) =>
        s"""libraryDependencies += "${maven.groupId}" % "${reference.artifact}" % "${reference.version}" cross CrossVersion.full"""
      case _ =>
        s"""libraryDependencies += "${maven.groupId}" %% "${reference.artifact}" % "${reference.version}""""
    }

    List(
      Some(install),
      resolver.flatMap(_.sbt.map("resolvers += " + _))
    ).flatten.mkString("\n")
  }

  /**
   * string representation for Ammonite loading
   * @return
   */
  def ammInstall: String = {

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
    ).flatten.mkString("\n")
  }

  /**
   * string representation for maven dependency
   * @return
   */
  def mavenInstall: String = {
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
  def gradleInstall: String = {
    import maven._
    s"compile group: '$groupId', name: '$artifactId', version: '$version'"
  }

  /**
   * string representation for mill dependency
   * @return
   */
  def millInstall: String = {
    def addResolver(r: Resolver) =
      r.url map (url => s"""MavenRepository("${url}")""")
    val artifactOperator = if (isNonStandardLib) ":" else "::"
    List(
      Some(
        s"""ivy"${maven.groupId}$artifactOperator${reference.artifact}:${reference.version}""""
      ),
      resolver flatMap addResolver
    ).flatten.mkString("\n")
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

  def scastieURL: Option[String] = {
    val tryBaseUrl = "https://scastie.scala-lang.org/try"

    def latestFor(version: String): String = {
      val latest =
        Map(
          "2.10" -> "2.10.7",
          "2.11" -> "2.11.12",
          "2.12" -> "2.12.6"
        )

      latest.get(version).getOrElse(version)
    }

    reference.target
      .map(target =>
        List(
          "g" -> maven.groupId,
          "a" -> reference.artifact,
          "v" -> maven.version,
          "t" -> target.targetType.toString.toUpperCase,
          "sv" -> latestFor(target.languageVersion.toString)
        )
      )
      .map(
        _.map { case (k, v) =>
          s"$k=$v"
        }.mkString(tryBaseUrl + "?", "&", "")
      )
  }

  def documentationURLs(
      documentationLinks: List[(String, String)]
  ): List[(String, String)] = {
    documentationLinks.map { case (label, url) => (label, evalLink(url)) }
  }

  /**
   * Documentation link are often related to a release version
   * for example: https://playframework.com/documentation/2.6.x/Home
   * we want to substitute input such as
   * https://playframework.com/documentation/[major].[minor].x/Home
   */
  private def evalLink(rawLink: String): String = {
    rawLink
      .replace("[groupId]", maven.groupId.toString)
      .replace("[artifactId]", maven.artifactId.toString)
      .replace("[version]", reference.version.toString)
      .replace("[major]", reference.version.major.toString)
      .replace("[minor]", reference.version.minor.toString)
      .replace("[name]", reference.artifact)
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

    def isValid: Boolean = {
      target.exists(_.isValid)
    }

    def projectReference: Project.Reference =
      Project.Reference(organization, repository)
    def name: String = s"$organization/$artifact"

    def artifactHttpPath: String = s"/$organization/$repository/$artifact"
    def artifactFullHttpUrl: String =
      s"https://index.scala-lang.org$artifactHttpPath"
    private def nonDefaultTargetType = {
      target.map(_.targetType).filter(_ != Jvm)
    }
    def badgeUrl: String =
      s"$artifactFullHttpUrl/latest-by-scala-version.svg" +
        nonDefaultTargetType.map("?targetType=" + _).mkString
    def httpUrl: String = {
      val targetQuery = target.map(t => s"?target=${t.encode}").getOrElse("")

      s"$artifactHttpPath/$version$targetQuery"
    }

    def isScalaLib: Boolean = {
      organization == "scala" &&
      repository == "scala" &&
      artifact == "scala-library"
    }
  }
}
