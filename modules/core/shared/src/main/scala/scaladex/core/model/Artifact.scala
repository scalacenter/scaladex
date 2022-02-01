package scaladex.core.model

import java.time.Instant

import fastparse.P
import fastparse.Start
import fastparse._
import scaladex.core.model.Project.DocumentationLink
import scaladex.core.util.Parsers

/**
 * @param isNonStandardLib if not using artifactName_scalaVersion convention
 */
case class Artifact(
    groupId: Artifact.GroupId,
    // artifactId is redundant with ArtifactName + platform
    // it's kept because we need to do joins with ArtifactDependency on MavenReference
    // It's also possible to create a new key for Artifact: ArtifactReference(GroupId, ArtifactId, SemanticVersion)
    // and keep untyped MavenReference ArtifactDependency.
    artifactId: String,
    version: SemanticVersion,
    artifactName: Artifact.Name,
    platform: Platform,
    projectRef: Project.Reference,
    description: Option[String],
    releaseDate: Option[Instant],
    resolver: Option[Resolver],
    licenses: Set[License],
    isNonStandardLib: Boolean
) {

  def fullPlatformVersion: String = platform.showVersion

  def isValid: Boolean = platform.isValid

  private def artifactHttpPath: String = s"/${projectRef.organization}/${projectRef.repository}/$artifactName"

  val mavenReference: Artifact.MavenReference = Artifact.MavenReference(groupId.value, artifactId, version.toString)

  def fullHttpUrl(env: Env): String =
    env match {
      case Env.Prod => s"https://index.scala-lang.org$artifactHttpPath"
      case Env.Dev =>
        s"https://index-dev.scala-lang.org$artifactHttpPath" // todo: fix locally
      case Env.Local =>
        s"http://localhost:8080$artifactHttpPath" // todo: fix locally
    }

  def httpUrl: String = {
    val targetQuery = s"?target=${platform.encode}"
    s"$artifactHttpPath/$version$targetQuery"
  }

  def badgeUrl(env: Env): String =
    s"${fullHttpUrl(env)}/latest-by-scala-version.svg" +
      (platform match {
        case _: Platform.ScalaJvm => ""
        case _                    => s"?targetType=${platform.platformType}"
      })

  def sbtInstall: String = {
    val install = platform match {
      case Platform.SbtPlugin(_, _) =>
        s"""addSbtPlugin("$groupId" % "$artifactName" % "$version")"""
      case _ if isNonStandardLib =>
        s"""libraryDependencies += "$groupId" % "$artifactName" % "$version""""
      case Platform.ScalaJs(_, _) | Platform.ScalaNative(_, _) =>
        s"""libraryDependencies += "$groupId" %%% "$artifactName" % "$version""""
      case Platform.ScalaJvm(ScalaVersion(_: PatchBinary)) =>
        s"""libraryDependencies += "$groupId" % "$artifactName" % "$version" cross CrossVersion.full"""
      case _ =>
        s"""libraryDependencies += "$groupId" %% "$artifactName" % "$version""""
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
        s"import $$ivy.`${groupId}$artifactOperator$artifactName:$version`"
      ),
      resolver.map(addResolver)
    ).flatten.mkString("\n")
  }

  /**
   * string representation for maven dependency
   * @return
   */
  def mavenInstall: String =
    s"""|<dependency>
        |  <groupId>$groupId</groupId>
        |  <artifactId>$artifactId</artifactId>
        |  <version>$version</version>
        |</dependency>""".stripMargin

  /**
   * string representation for gradle dependency
   * @return
   */
  def gradleInstall: String =
    s"compile group: '$groupId', name: '$artifactId', version: '$version'"

  /**
   * string representation for mill dependency
   * @return
   */
  def millInstall: String = {
    def addResolver(r: Resolver): Option[String] =
      r.url.map(url => s"""MavenRepository("${url}")""")
    val artifactOperator = if (isNonStandardLib) ":" else "::"
    List(
      Some(s"""ivy"$groupId$artifactOperator$artifactName:$version""""),
      resolver.flatMap(addResolver)
    ).flatten.mkString("\n")
  }

  def scaladoc(scaladocPattern: Option[String]): Option[String] =
    (scaladocPattern, resolver) match {
      case (None, None) =>
        Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
      case (None, Some(_))    => None
      case (Some(pattern), _) => Some(evalLink(pattern))
    }

  // todo: Add tests for this
  def scastieURL: String = {
    val tryBaseUrl = "https://scastie.scala-lang.org/try"

    def latestFor(version: String): String = {
      val latest =
        Map(
          "2.10" -> "2.10.7",
          "2.11" -> "2.11.12",
          "2.12" -> "2.12.6"
        )

      latest.getOrElse(version, version)
    }
    List(
      "g" -> groupId,
      "a" -> artifactName.value,
      "v" -> version,
      "t" -> platform.platformType.toString.toUpperCase,
      "sv" -> latestFor(platform.scalaVersion.toString)
    )
      .map { case (k, v) => s"$k=$v" }
      .mkString(tryBaseUrl + "?", "&", "")
  }

  def documentationLinks(patterns: List[DocumentationLink]): List[DocumentationLink] =
    patterns.map { case DocumentationLink(label, url) => DocumentationLink(label, evalLink(url)) }

  /**
   * Documentation link are often related to a release version
   * for example: https://playframework.com/documentation/2.6.x/Home
   * we want to substitute input such as
   * https://playframework.com/documentation/[major].[minor].x/Home
   */
  private def evalLink(pattern: String): String =
    pattern
      .replace("[groupId]", groupId.toString)
      .replace("[artifactId]", artifactId)
      .replace("[version]", version.toString)
      .replace("[major]", version.major.toString)
      .replace("[minor]", version.minor.toString)
      .replace("[name]", artifactName.value)

}

object Artifact {
  case class Name(value: String) extends AnyVal {
    override def toString: String = value
  }
  object Name {
    implicit val ordering: Ordering[Name] = Ordering.by(_.value)
  }

  case class GroupId(value: String) extends AnyVal {
    override def toString: String = value
  }
  case class ArtifactId(name: Name, platform: Platform) {
    def value: String = s"${name}${platform.encode}"
  }

  object ArtifactId extends Parsers {
    import fastparse.NoWhitespace._

    private def FullParser[_: P] = {
      Start ~
        (Alpha | Digit | "-" | "." | (!(Platform.IntermediateParser ~ End) ~ "_")).rep.! ~ // must end with scala target
        Platform.Parser ~
        End
    }.map {
      case (name, target) =>
        ArtifactId(Name(name), target)
    }

    def parse(artifactId: String): Option[ArtifactId] =
      tryParse(artifactId, x => FullParser(x))
  }

  case class MavenReference(groupId: String, artifactId: String, version: String) {

    def name: String = s"$groupId/$artifactId"

    /**
     * url to maven page with related information to this reference
     */
    def httpUrl: String =
      s"http://search.maven.org/#artifactdetails|$groupId|$artifactId|$version|jar"
  }

}
