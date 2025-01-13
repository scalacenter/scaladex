package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.*
import scaladex.core.service.ProjectService

import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.apache.pekko.http.scaladsl.model.headers
import org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.RouteResult

class Badges(projectService: ProjectService)(using ExecutionContext):

  private val shields =
    parameters("color".?, "style".?, "logo".?, "logoWidth".as[Int].?)

  private val shieldsOptionalSubject = shields & parameters("subject".?)

  val route: Route =
    get {
      concat(
        path(projectM / "latest.svg")(ref => latest(ref, None)),
        path(projectM / artifactNameM / "latest.svg")((ref, artifact) => latest(ref, Some(artifact))),
        path(projectM / artifactNameM / "latest-by-scala-version.svg") { (ref, artifact) =>
          latestByScalaVersion(ref, artifact)
        }
      )
    }

  private def shieldsSvg(
      rawSubject: String,
      rawStatus: String,
      rawColor: Option[String],
      style: Option[String],
      logo: Option[String],
      logoWidth: Option[Int]
  ) =
    def shieldEscape(in: String): String =
      in.replace("-", "--").replace("_", "__").replace(" ", "_")

    val subject = shieldEscape(rawSubject)
    val status = shieldEscape(rawStatus)
    val color = rawColor.getOrElse("green")

    // we need a specific encoding
    val query = List(
      style.map(("style", _)),
      logo.map(l => ("logo", java.net.URLEncoder.encode(l, "ascii").replace("+", "%2B"))),
      logoWidth.map(w => ("logoWidth", w.toString))
    ).flatten.map { case (k, v) => k + "=" + v }.mkString("?", "&", "")

    respondWithHeaders(headers.`Cache-Control`(`no-cache`), headers.ETag(status)) {
      redirect(s"https://img.shields.io/badge/$subject-$status-$color.svg$query", TemporaryRedirect)
    }
  end shieldsSvg

  def latest(ref: Project.Reference, artifactName: Option[Artifact.Name]): RequestContext => Future[RouteResult] =
    parameter("target".?) { binaryVersion =>
      shieldsOptionalSubject { (color, style, logo, logoWidth, subjectOpt) =>
        val subject = subjectOpt.orElse(artifactName.map(_.value)).getOrElse(ref.repository.value)
        def error(msg: String) = shieldsSvg(subject, msg, color.orElse(Some("lightgrey")), style, logo, logoWidth)

        val res = projectService.getProject(ref).flatMap {
          case None => Future.successful(error("project not found"))
          case Some(project) =>
            val bv = binaryVersion.flatMap(BinaryVersion.parse)
            getDefaultArtifact(project, bv, artifactName).map {
              case None => error("no published artifacts")
              case Some(artifact) => shieldsSvg(subject, artifact.version.toString, color, style, logo, logoWidth)
            }

        }
        onSuccess(res)(identity)
      }
    }

  def latestByScalaVersion(ref: Project.Reference, artifactName: Artifact.Name): RequestContext => Future[RouteResult] =
    // targetType parameter is kept for forward compatibility
    // in case targetType is defined we choose the most recent corresponding platform
    parameters("targetType".?, "platform".?) { (targetTypeParam, platformParam) =>
      shields { (color, style, logo, logoWidth) =>
        val headerF = projectService.getHeader(ref).map(_.get)
        onSuccess(headerF) { header =>
          val platforms = header.platforms(artifactName)
          val platform = platformParam
            .flatMap(Platform.parse)
            .orElse(targetTypeParam.flatMap(selectPlatformFromTargetType(_, platforms)))
            .getOrElse(platforms.max)
          val artifacts = header.artifacts(artifactName, platform)
          val summary = Badges.summaryOfLatestVersions(artifacts.map(_.reference), platform)
          shieldsSvg(s"$artifactName - $platform", summary, color, style, logo, logoWidth)
        }
      }
    }

  private def selectPlatformFromTargetType(targetType: String, platforms: Seq[Platform]): Option[Platform] =
    targetType.toUpperCase match
      case "JVM" => platforms.find(_ == Jvm)
      case "JS" => platforms.collect { case p: ScalaJs => p }.maxOption
      case "NATIVE" => platforms.collect { case v: ScalaNative => v }.maxOption
      case "SBT" => platforms.collect { case v: SbtPlugin => v }.maxOption
      case _ => None

  private def getDefaultArtifact(
      project: Project,
      binaryVersion: Option[BinaryVersion],
      artifact: Option[Artifact.Name]
  ): Future[Option[Artifact.Reference]] =
    projectService.getHeader(project.reference).map(_.map(_.getDefaultArtifact0(binaryVersion, artifact).reference))
end Badges

object Badges:
  private def summaryOfLatestVersions(artifacts: Seq[Artifact.Reference], platform: Platform): String =
    platform match
      case _: (SbtPlugin | MillPlugin) =>
        val latestVersion = artifacts.map(_.version).max
        latestVersion.toString
      case _ =>
        val latestVersions = artifacts.groupMapReduce(_.binaryVersion.language)(_.version)(Version.ordering.max)
        summaryByLanguageVersion(latestVersions)

  private[route] def summaryByLanguageVersion(latestVersions: Map[Language, Version]): String =
    latestVersions.toSeq
      .groupMap { case (_, latestVersion) => latestVersion } { case (language, _) => language }
      .toSeq
      .sortBy(_._1)(Version.ordering.reverse)
      .map {
        case (version, Seq(Java)) => s"$version"
        case (version, languages) =>
          // there is more than one language, we ignore Java
          val scalaVersions =
            languages.collect { case Scala(v) => v }.toSeq.sorted(Version.ordering.reverse).mkString(", ")
          s"$version (Scala $scalaVersions)"
      }
      .mkString(", ")
end Badges
