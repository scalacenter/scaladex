package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.License
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.Jvm
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.PatchBinary
import ch.epfl.scala.index.model.release.Resolver
import ch.epfl.scala.index.model.release.SbtPlugin
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaJs
import ch.epfl.scala.index.model.release.ScalaJvm
import ch.epfl.scala.index.model.release.ScalaNative
import ch.epfl.scala.index.model.release.ScalaTarget
import ch.epfl.scala.index.model.release.ScalaTargetType
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject.DocumentationLink
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease._
import org.joda.time.DateTime
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
    target: Option[ScalaTarget], // Todo: Include JAVA HERE and remove Option
    description: Option[String],
    released: Option[DateTime],
    resolver: Option[Resolver],
    licenses: Set[License],
    isNonStandardLib: Boolean
) {
  def targetType: ScalaTargetType = target.map(_.targetType).getOrElse(Jvm)

  def projectRef: Project.Reference =
    Project.Reference(organization.value, repository.value)

  def scalaVersion: String = target.map(_.showVersion).getOrElse("Java")

  def scalaJsVersion: Option[String] = ???

  def scalaNativeVersion: Option[String] = ???

  def sbtVersion: Option[String] = ???

  val reference: Release.Reference = Release.Reference(
    organization = organization.value,
    repository = repository.value,
    artifact = artifactName.value,
    version = version,
    target = target
  )

  def sbtInstall: String = {
    val install = target match {
      case Some(SbtPlugin(_, _)) =>
        s"""addSbtPlugin("${maven.groupId}" % "${artifactName}" % "${version}")"""
      case _ if isNonStandardLib =>
        s"""libraryDependencies += "${maven.groupId}" % "${artifactName}" % "${version}""""
      case Some(ScalaJs(_, _) | ScalaNative(_, _)) =>
        s"""libraryDependencies += "${maven.groupId}" %%% "${artifactName}" % "${version}""""
      case Some(ScalaJvm(ScalaVersion(_: PatchBinary))) | Some(
            ScalaJvm(Scala3Version(_: PatchBinary))
          ) =>
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
    s"""|<dependency>
        |  <groupId>${maven.groupId}</groupId>
        |  <artifactId>${maven.artifactId}</artifactId>
        |  <version>$version</version>
        |</dependency>""".stripMargin
  }

  /**
   * string representation for gradle dependency
   * @return
   */
  def gradleInstall: String = {
    s"compile group: '${maven.groupId}', name: '${maven.artifactId}', version: '${maven.version}'"
  }

  /**
   * string representation for mill dependency
   * @return
   */
  def millInstall: String = {
    def addResolver(r: Resolver): Option[String] =
      r.url map (url => s"""MavenRepository("${url}")""")
    val artifactOperator = if (isNonStandardLib) ":" else "::"
    List(
      Some(
        s"""ivy"${maven.groupId}$artifactOperator${reference.artifact}:${reference.version}""""
      ),
      resolver flatMap addResolver
    ).flatten.mkString("\n")
  }

  def scalaDocURL(customScalaDoc: Option[String]): Option[String] = {
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
  }

  //todo: Add tests for this
  def scastieURL: Option[String] = {
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

    reference.target
      .map(target =>
        List(
          "g" -> maven.groupId,
          "a" -> artifactName.value,
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
      documentationLinks: List[DocumentationLink]
  ): List[DocumentationLink] = {
    documentationLinks.map { case DocumentationLink(label, url) =>
      DocumentationLink(label, evalLink(url))
    }
  }

  /**
   * Documentation link are often related to a release version
   * for example: https://playframework.com/documentation/2.6.x/Home
   * we want to substitute input such as
   * https://playframework.com/documentation/[major].[minor].x/Home
   */
  private def evalLink(rawLink: String): String = {
    rawLink
      .replace("[groupId]", maven.groupId)
      .replace("[artifactId]", maven.artifactId)
      .replace("[version]", reference.version.toString)
      .replace("[major]", reference.version.major.toString)
      .replace("[minor]", reference.version.minor.toString)
      .replace("[name]", reference.artifact)
  }

}

object NewRelease {
  val format = ISODateTimeFormat.dateTime.withOffsetParsed
  case class ArtifactName(value: String) extends AnyVal {
    override def toString: String = value
  }

  def from(r: Release): NewRelease = {
    NewRelease(
      maven = r.maven,
      organization = Organization(r.reference.organization),
      repository = Repository(r.reference.repository),
      artifactName = ArtifactName(r.reference.artifact),
      version = r.reference.version,
      target = r.reference.target,
      description = r.description,
      released = r.released.map(format.parseDateTime),
      resolver = r.resolver,
      licenses = r.licenses,
      isNonStandardLib = r.isNonStandardLib
    )
  }
}
