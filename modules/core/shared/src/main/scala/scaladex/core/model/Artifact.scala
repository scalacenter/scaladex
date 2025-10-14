package scaladex.core.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import scaladex.core.api.ArtifactResponse
import scaladex.core.model.Artifact.*
import scaladex.core.util.Parsers.*

import fastparse.*

/** @param isNonStandardLib
  *   if not using artifact-name_binaryVersion convention
  */
case class Artifact(
    groupId: GroupId,
    artifactId: ArtifactId,
    version: Version,
    projectRef: Project.Reference,
    description: Option[String],
    releaseDate: Instant,
    resolver: Option[Resolver],
    licenses: Set[License],
    isNonStandardLib: Boolean,
    fullScalaVersion: Option[Version],
    scaladocUrl: Option[Url],
    versionScheme: Option[String],
    developers: Seq[Contributor] = Seq.empty
):
  val reference: Reference = Reference(groupId, artifactId, version)
  def binaryVersion: BinaryVersion = artifactId.binaryVersion
  def language: Language = binaryVersion.language
  def platform: Platform = binaryVersion.platform
  def name: Artifact.Name = artifactId.name

  def isValid: Boolean = binaryVersion.isValid

  def groupIdAndName: String =
    val sep = binaryVersion match
      case BinaryVersion(Jvm, Java) | BinaryVersion(SbtPlugin(_), _) => ":"
      case BinaryVersion(ScalaJs(_) | ScalaNative(_), _) => ":::"
      case _ => "::"
    s"$groupId$sep$name"

  def releaseDateFormat: String = Artifact.dateFormatter.format(releaseDate)

  def httpUrl: String = s"$artifactHttpPath/$version?binary-version=${binaryVersion.value}"

  def badgeUrl(env: Env, platform: Option[Platform] = None): String =
    s"${fullHttpUrl(env)}/latest-by-scala-version.svg?platform=${platform.map(_.value).getOrElse(this.platform.value)}"

  // TODO move this out
  def fullHttpUrl(env: Env): String = env.rootUrl + artifactHttpPath

  private def artifactHttpPath: String = s"/${projectRef.organization}/${projectRef.repository}/$name"

  def latestBadgeUrl(env: Env): String =
    s"${fullHttpUrl(env)}/latest.svg"

  def sbtInstall: Option[String] =
    val install = platform match
      case SbtPlugin(_) => Some(s"""addSbtPlugin("$groupId" % "$name" % "$version")""")
      case MillPlugin(_) => None
      case _ if isNonStandardLib => Some(s"""libraryDependencies += "$groupId" % "$artifactId" % "$version"""")
      case ScalaJs(_) | ScalaNative(_) =>
        Some(s"""libraryDependencies += "$groupId" %%% "$name" % "$version"""")
      case Jvm =>
        language match
          case Java => Some(s"""libraryDependencies += "$groupId" % "$name" % "$version"""")
          case Scala(Version.Patch(_, _, _)) =>
            Some(s"""libraryDependencies += "$groupId" % "$name" % "$version" cross CrossVersion.full""")
          case _ => Some(s"""libraryDependencies += "$groupId" %% "$name" % "$version"""")
        case CompilerPlugin => throw new UnsupportedOperationException("CompilerPlugin not supported in sbtInstall")

    (install, resolver.flatMap(_.sbt)) match
      case (None, _) => None
      case (Some(install), None) => Some(install)
      case (Some(install), Some(resolver)) =>
        Some(
          s"""|$install
              |resolvers += $resolver""".stripMargin
        )
  end sbtInstall

  /** string representation for Ammonite loading
    * @return
    */
  def ammInstall: Option[String] =
    def addResolver(r: Resolver) =
      s"""|import ammonite._, Resolvers._
          |val res = Resolver.Http(
          |  "${r.name}",
          |  "${r.url}",
          |  IvyPattern,
          |  false)
          |interp.resolvers() = interp.resolvers() :+ res""".stripMargin

    val install = platform match
      case MillPlugin(_) | SbtPlugin(_) | ScalaNative(_) | ScalaJs(_) => None
      case Jvm =>
        language match
          case _ if isNonStandardLib => Some(s"import $$ivy.`$groupId:$artifactId:$version`")
          case Java => Some(s"import $$ivy.`$groupId:$artifactId:$version`")
          case Scala(Version.Patch(_, _, _)) => Some(s"import $$ivy.`$groupId:::$name:$version`")
          case _ => Some(s"import $$ivy.`$groupId::$name:$version`")
        case CompilerPlugin => throw new UnsupportedOperationException("CompilerPlugin not supported in ammInstall")

    (install, resolver.map(addResolver)) match
      case (None, _) => None
      case (Some(install), None) => Some(install)
      case (Some(install), Some(resolver)) => Some(s"$install\n$resolver")
  end ammInstall

  /** string representation for maven dependency
    * @return
    */
  def mavenInstall: Option[String] =
    platform match
      case MillPlugin(_) | SbtPlugin(_) | ScalaNative(_) | ScalaJs(_) => None
      case Jvm =>
        Some(
          s"""|<dependency>
              |  <groupId>$groupId</groupId>
              |  <artifactId>$artifactId</artifactId>
              |  <version>$version</version>
              |</dependency>""".stripMargin
        )
        case CompilerPlugin => throw new UnsupportedOperationException("CompilerPlugin not supported in mavenInstall")

  /** string representation for gradle dependency
    * @return
    */
  def gradleInstall: Option[String] =
    platform match
      case MillPlugin(_) | SbtPlugin(_) | ScalaNative(_) | ScalaJs(_) => None
      case Jvm => Some(s"compile group: '$groupId', name: '$artifactId', version: '$version'")
        case CompilerPlugin => throw new UnsupportedOperationException("CompilerPlugin not supported in gradleInstall")

  /** string representation for mill dependency
    * @return
    */
  def millInstall: Option[String] =
    val install = platform match
      case MillPlugin(_) => Some(s"import $$ivy.`$groupId::$name::$version`")
      case SbtPlugin(_) => None
      case ScalaNative(_) | ScalaJs(_) => Some(s"""ivy"$groupId::$name::$version"""")
      case Jvm =>
        language match
          case _ if isNonStandardLib => Some(s"""ivy"$groupId:$artifactId:$version"""")
          case Java => Some(s"""ivy"$groupId:$artifactId:$version"""")
          case Scala(Version.Patch(_, _, _)) => Some(s"""ivy"$groupId:::$name:$version"""")
          case _ => Some(s"""ivy"$groupId::$name:$version"""")
        case CompilerPlugin => throw new UnsupportedOperationException("CompilerPlugin not supported in millInstall")
    (install, resolver.flatMap(_.url)) match
      case (None, _) => None
      case (Some(install), None) => Some(install)
      case (Some(install), Some(resolverUrl)) =>
        Some(
          s"""|$install
              |MavenRepository("$resolverUrl")""".stripMargin
        )
  end millInstall

  def scalaCliInstall: Option[String] =
    binaryVersion.platform match
      case MillPlugin(_) | SbtPlugin(_) => None
      case ScalaNative(_) | ScalaJs(_) => Some(s"""//> using dep "$groupId::$name::$version"""")
      case Jvm =>
        language match
          case _ if isNonStandardLib => Some(s"""//> using dep "$groupId:$artifactId:$version"""")
          case Java => Some(s"""//> using dep "$groupId:$artifactId:$version"""")
          case Scala(Version.Patch(_, _, _)) => Some(s"""//> using dep "$groupId:::$name:$version"""")
          case _ => Some(s"""//> using dep "$groupId::$name:$version"""")
        case CompilerPlugin => throw new UnsupportedOperationException("CompilerPlugin not supported in scalaCliInstall")

  def csLaunch: Option[String] =
    platform match
      case MillPlugin(_) | SbtPlugin(_) => None
      case ScalaNative(_) | ScalaJs(_) => Some(s"cs launch $groupId::$name::$version")
      case Jvm =>
        language match
          case _ if isNonStandardLib => Some(s"cs launch $groupId:$artifactId:$version")
          case Java => Some(s"cs launch $groupId:$artifactId:$version")
          case Scala(Version.Patch(_, _, _)) => Some(s"cs launch $groupId:::$name:$version")
          case _ => Some(s"cs launch $groupId::$name:$version")
        case CompilerPlugin => throw new UnsupportedOperationException("CompilerPlugin not supported in csLaunch")

  def defaultScaladoc: Option[String] =
    resolver match
      case None => Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
      case _ => None

  def scastieURL: Option[String] =
    val tryBaseUrl = "https://scastie.scala-lang.org/try"

    val targetParam = platform match
      case ScalaJs(_) => Some("t" -> "JS")
      case Jvm => Some("t" -> "JVM")
      case _ => None

    val scalaVersionParam = language match
      case Scala(v) => Some("sv" -> v.toString)
      case _ => None

    for
      target <- targetParam
      scalaVersion <- scalaVersionParam
    yield
      val params: List[(String, String)] = List(
        "g" -> groupId.value,
        "a" -> name.value,
        "v" -> version.value,
        "o" -> projectRef.organization.toString,
        "r" -> projectRef.repository.toString,
        target,
        scalaVersion
      )
      params.map { case (k, v) => s"$k=$v" }.mkString(tryBaseUrl + "?", "&", "")
    end for
  end scastieURL

  def toResponse: ArtifactResponse =
    ArtifactResponse(
      groupId,
      artifactId,
      version,
      name,
      binaryVersion,
      language,
      platform,
      projectRef,
      releaseDate,
      licenses.toSeq
    )
end Artifact

object Artifact:
  private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, uuuu").withZone(ZoneOffset.UTC)

  case class Name(value: String) extends AnyVal:
    override def toString: String = value
  object Name:
    given ordering: Ordering[Name] = Ordering.by(_.value)

  case class GroupId(value: String) extends AnyVal:
    override def toString: String = value
    def mavenUrl: String = value.replace('.', '/')
  object GroupId:
    given ordering: Ordering[GroupId] = Ordering.by(_.value)

  case class ArtifactId(name: Name, binaryVersion: BinaryVersion):
    override def toString = value
    def value: String = s"$name${binaryVersion.asSuffix}"
    def isScala: Boolean = binaryVersion.language.isScala

  object ArtifactId:
    import fastparse.NoWhitespace.*

    private def FullParser[A: P] = {
      Start ~
        (Alpha | Digit | "-" | "." | (!(BinaryVersion.IntermediateParserButNotInvalidSbt ~ End) ~ "_")).rep.! ~ // must end with scala target
        BinaryVersion.Parser ~
        End
    }.map {
      case (name, binaryVersion) =>
        ArtifactId(Name(name), binaryVersion)
    }

    def apply(artifactId: String): ArtifactId =
      tryParse(artifactId, x => FullParser(x)).getOrElse(ArtifactId(Name(artifactId), BinaryVersion(Jvm, Java)))
  end ArtifactId

  case class Reference(groupId: GroupId, artifactId: ArtifactId, version: Version):
    override def toString(): String = s"$groupId:$artifactId:$version"

    def name: Name = artifactId.name
    def binaryVersion: BinaryVersion = artifactId.binaryVersion
    def platform: Platform = binaryVersion.platform
    def language: Language = binaryVersion.language

    def searchUrl: String =
      s"https://search.maven.org/#artifactdetails|$groupId|$artifactId|$version|jar"

    def repoUrl: String =
      s"https://repo1.maven.org/maven2/${groupId.value.replace('.', '/')}/$artifactId/$version/"
  end Reference

  object Reference:
    def from(groupId: String, artifactId: String, version: String): Reference =
      Reference(GroupId(groupId), ArtifactId(artifactId), Version(version))
end Artifact
