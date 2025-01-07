package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactSelection
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Jvm
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.Version
import scaladex.core.model.Version.PreferStable
import scaladex.core.service.WebDatabase

import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.*
import org.apache.pekko.http.scaladsl.model.headers.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.RouteResult

class Badges(database: WebDatabase)(using ExecutionContext):

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
      in.replace("-", "--")
        .replace("_", "__")
        .replace(" ", "_")

    val subject = shieldEscape(rawSubject)
    val status = shieldEscape(rawStatus)

    val color = rawColor.getOrElse("green")

    // we need a specific encoding
    val query = List(
      style.map(("style", _)),
      logo.map(l =>
        (
          "logo",
          java.net.URLEncoder
            .encode(l, "ascii")
            .replace("+", "%2B")
        )
      ),
      logoWidth.map(w => ("logoWidth", w.toString))
    ).flatten.map { case (k, v) => k + "=" + v }.mkString("?", "&", "")

    respondWithHeaders(`Cache-Control`(`no-cache`), ETag(status)) {
      redirect(
        s"https://img.shields.io/badge/$subject-$status-$color.svg$query",
        TemporaryRedirect
      )
    }
  end shieldsSvg

  def latest(
      ref: Project.Reference,
      artifactName: Option[Artifact.Name]
  ): RequestContext => Future[RouteResult] =
    parameter("target".?) { binaryVersion =>
      shieldsOptionalSubject { (color, style, logo, logoWidth, subjectOpt) =>
        val subject = subjectOpt.orElse(artifactName.map(_.value)).getOrElse(ref.repository.value)
        def error(msg: String) =
          shieldsSvg(subject, msg, color.orElse(Some("lightgrey")), style, logo, logoWidth)

        val res = database.getProject(ref).flatMap {
          case None => Future.successful(error("project not found"))
          case Some(project) =>
            val bv = binaryVersion.flatMap(BinaryVersion.parse)
            getDefaultArtifact(project, bv, artifactName).map {
              case None => error("no published artifacts")
              case Some(artifact) =>
                shieldsSvg(subject, artifact.version.toString, color, style, logo, logoWidth)
            }

        }
        onSuccess(res)(identity)
      }
    }

  def latestByScalaVersion(
      reference: Project.Reference,
      artifactName: Artifact.Name
  ): RequestContext => Future[RouteResult] =
    // targetType paramater is kept for forward compatibility
    // in case targetType is defined we choose the most recent corresponding platform
    parameters("targetType".?, "platform".?) { (targetTypeParam, platformParam) =>
      shields { (color, style, logo, logoWidth) =>
        // TODO use projectHeader
        val artifactsF = database.getProjectArtifactRefs(reference, artifactName)
        onSuccess(artifactsF) { artifacts =>
          val availablePlatforms = artifacts.map(_.binaryVersion.platform).distinct
          val platform = platformParam
            .flatMap(Platform.parse)
            .orElse {
              targetTypeParam.map(_.toUpperCase).flatMap {
                case "JVM" => Some(Jvm)
                case "JS" =>
                  val jsPlatforms =
                    availablePlatforms.collect { case p: ScalaJs => p }
                  Option.when(jsPlatforms.nonEmpty)(jsPlatforms.max[Platform])
                case "NATIVE" =>
                  val nativePlatforms =
                    availablePlatforms.collect { case v: ScalaNative => v }
                  Option.when(nativePlatforms.nonEmpty)(nativePlatforms.max[Platform])
                case "SBT" =>
                  val sbtPlatforms =
                    availablePlatforms.collect { case v: SbtPlugin => v }
                  Option.when(sbtPlatforms.nonEmpty)(sbtPlatforms.max[Platform])
                case _ => None
              }
            }
            .getOrElse(availablePlatforms.max)

          val platformArtifacts = artifacts.filter(_.binaryVersion.platform == platform)
          val summary = Badges.summaryOfLatestVersions(platformArtifacts)

          shieldsSvg(s"$artifactName - $platform", summary, color, style, logo, logoWidth)
        }
      }
    }

  private def getDefaultArtifact(
      project: Project,
      binaryVersion: Option[BinaryVersion],
      artifact: Option[Artifact.Name]
  ): Future[Option[Artifact.Reference]] =
    val artifactSelection = ArtifactSelection(binaryVersion, artifact)
    // TODO use projectHeader
    database.getProjectArtifactRefs(project.reference, stableOnly = false).map { artifacts =>
      val (stableArtifacts, nonStableArtifacts) = artifacts.partition(_.version.isStable)
      artifactSelection
        .defaultArtifact(stableArtifacts, project)
        .orElse(artifactSelection.defaultArtifact(nonStableArtifacts, project))
    }
  end getDefaultArtifact
end Badges

object Badges:
  private def summaryOfLatestVersions(artifacts: Seq[Artifact.Reference]): String =
    val versionsByScalaVersions = artifacts
      .groupMap(_.binaryVersion.language)(_.version)
      .collect { case (Scala(v), version) => Scala(v) -> version }
    summaryOfLatestVersions(versionsByScalaVersions)

  private[route] def summaryOfLatestVersions(versionsByScalaVersions: Map[Scala, Seq[Version]]): String =
    versionsByScalaVersions.view
      .mapValues(_.max(PreferStable))
      .groupMap { case (_, latestVersion) => latestVersion } { case (scalaVersion, _) => scalaVersion }
      .toSeq
      .sortBy(_._1)(Version.ordering.reverse)
      .map {
        case (latestVersion, scalaVersions) =>
          val scalaVersionsStr =
            scalaVersions.map(_.version).toSeq.sorted(Version.ordering.reverse).mkString(", ")
          s"$latestVersion (Scala $scalaVersionsStr)"
      }
      .mkString(", ")
end Badges
