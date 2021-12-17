package ch.epfl.scala.index.newModel

import java.time.Instant

import scala.util.Try

import ch.epfl.scala.index.model.Env
import ch.epfl.scala.index.model.License
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.PatchBinary
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Resolver
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject.DocumentationLink
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease._
import org.joda.time.format.ISODateTimeFormat

/**
 * Artifact release representation
 *
 * @param isNonStandardLib if not using artifactName_scalaVersion convention
 */

case class NewRelease(
    maven: MavenReference,
    version: SemanticVersion,
    organization: Organization,
    repository: Repository,
    artifactName: ArtifactName,
    platform: Platform,
    description: Option[String],
    releasedAt: Option[Instant],
    resolver: Option[Resolver],
    licenses: Set[License],
    isNonStandardLib: Boolean
) {
  def projectRef: NewProject.Reference =
    NewProject.Reference(organization, repository)

  def fullPlatformVersion: String = platform.showVersion

  def isValid: Boolean = platform.isValid

  def name: String = s"$organization/$artifactName"
  private def artifactHttpPath: String =
    s"/$organization/$repository/$artifactName"

  def artifactFullHttpUrl(env: Env): String =
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
    s"${artifactFullHttpUrl(env)}/latest-by-scala-version.svg" +
      (platform match {
        case _: Platform.ScalaJvm => ""
        case _                    => s"?targetType=${platform.platformType}"
      })

  def sbtInstall: String = {
    val install = platform match {
      case Platform.SbtPlugin(_, _) =>
        s"""addSbtPlugin("${maven.groupId}" % "${artifactName}" % "${version}")"""
      case _ if isNonStandardLib =>
        s"""libraryDependencies += "${maven.groupId}" % "${artifactName}" % "${version}""""
      case Platform.ScalaJs(_, _) | Platform.ScalaNative(_, _) =>
        s"""libraryDependencies += "${maven.groupId}" %%% "${artifactName}" % "${version}""""
      case Platform.ScalaJvm(ScalaVersion(_: PatchBinary)) | Platform.ScalaJvm(Scala3Version(_: PatchBinary)) =>
        s"""libraryDependencies += "${maven.groupId}" % "${artifactName}" % "${version}" cross CrossVersion.full"""
      case _ =>
        s"""libraryDependencies += "${maven.groupId}" %% "${artifactName}" % "${version}""""
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
        s"import $$ivy.`${maven.groupId}$artifactOperator$artifactName:$version`"
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
        |  <groupId>${maven.groupId}</groupId>
        |  <artifactId>${maven.artifactId}</artifactId>
        |  <version>$version</version>
        |</dependency>""".stripMargin

  /**
   * string representation for gradle dependency
   * @return
   */
  def gradleInstall: String =
    s"compile group: '${maven.groupId}', name: '${maven.artifactId}', version: '${maven.version}'"

  /**
   * string representation for mill dependency
   * @return
   */
  def millInstall: String = {
    def addResolver(r: Resolver): Option[String] =
      r.url.map(url => s"""MavenRepository("${url}")""")
    val artifactOperator = if (isNonStandardLib) ":" else "::"
    List(
      Some(s"""ivy"${maven.groupId}$artifactOperator$artifactName:$version""""),
      resolver.flatMap(addResolver)
    ).flatten.mkString("\n")
  }

  def scalaDocURL(customScalaDoc: Option[String]): Option[String] =
    customScalaDoc match {
      case None =>
        if (resolver.isEmpty) {
          /* no frame
           * hosted on s3 at:
           *https://static.javadoc.io/$groupId/$artifactId/$version/index.html#package
           * HEAD to check 403 vs 200
           */
          Some(
            s"https://www.javadoc.io/doc/${maven.groupId}/${maven.artifactId}/$version"
          )
        } else None
      case Some(rawLink) => Some(evalLink(rawLink))
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
      "g" -> maven.groupId,
      "a" -> artifactName.value,
      "v" -> maven.version,
      "t" -> platform.platformType.toString.toUpperCase,
      "sv" -> latestFor(platform.scalaVersion.toString)
    )
      .map { case (k, v) => s"$k=$v" }
      .mkString(tryBaseUrl + "?", "&", "")
  }

  def documentationURLs(
      documentationLinks: List[DocumentationLink]
  ): List[DocumentationLink] =
    documentationLinks.map { case DocumentationLink(label, url) => DocumentationLink(label, evalLink(url)) }

  /**
   * Documentation link are often related to a release version
   * for example: https://playframework.com/documentation/2.6.x/Home
   * we want to substitute input such as
   * https://playframework.com/documentation/[major].[minor].x/Home
   */
  private def evalLink(rawLink: String): String =
    rawLink
      .replace("[groupId]", maven.groupId)
      .replace("[artifactId]", maven.artifactId)
      .replace("[version]", version.toString)
      .replace("[major]", version.major.toString)
      .replace("[minor]", version.minor.toString)
      .replace("[name]", artifactName.value)

}

object NewRelease {
  val format = ISODateTimeFormat.dateTime.withOffsetParsed
  case class ArtifactName(value: String) extends AnyVal {
    override def toString: String = value
  }
  object ArtifactName {
    implicit val ordering: Ordering[ArtifactName] = Ordering.by(_.value)
  }

  // we used to write joda.DateTime and now we moved to Instant
  private def parseToInstant(s: String): Option[Instant] =
    Try(format.parseDateTime(s)).map(t => Instant.ofEpochMilli(t.getMillis)).orElse(Try(Instant.parse(s))).toOption
}
