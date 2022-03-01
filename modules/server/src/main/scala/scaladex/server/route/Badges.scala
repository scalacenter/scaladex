package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult
import scaladex.core.model.Artifact
import scaladex.core.model.Jvm
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.SemanticVersion
import scaladex.core.service.WebDatabase

class Badges(database: WebDatabase)(implicit executionContext: ExecutionContext) {

  private val shields =
    parameters("color".?, "style".?, "logo".?, "logoWidth".as[Int].?)

  private val shieldsOptionalSubject = shields & parameters("subject".?)

  val route: Route =
    get {
      concat(
        path(organizationM / repositoryM / "latest.svg")((org, repo) => latest(org, repo, None)),
        path(organizationM / repositoryM / artifactM / "latest.svg") { (org, repo, artifact) =>
          latest(org, repo, Some(artifact))
        },
        path(
          organizationM / repositoryM / artifactM / "latest-by-scala-version.svg"
        )((org, repo, artifact) => latestByScalaVersion(Project.Reference(org, repo), artifact))
      )
    }

  private def shieldsSvg(
      rawSubject: String,
      rawStatus: String,
      rawColor: Option[String],
      style: Option[String],
      logo: Option[String],
      logoWidth: Option[Int]
  ) = {

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

    respondWithHeader(`Cache-Control`(`no-cache`)) {
      redirect(
        s"https://img.shields.io/badge/$subject-$status-$color.svg$query",
        TemporaryRedirect
      )
    }
  }

  def latest(
      organization: Project.Organization,
      repository: Project.Repository,
      artifactName: Option[Artifact.Name]
  ): RequestContext => Future[RouteResult] =
    parameter("target".?) { binaryVersion =>
      shieldsOptionalSubject { (color, style, logo, logoWidth, subject) =>
        val res = getSelectedArtifact(
          database,
          organization,
          repository,
          binaryVersion,
          artifactName,
          version = None,
          selected = None
        )
        onSuccess(res) {
          case Some(artifact) =>
            shieldsSvg(
              subject.orElse(artifactName.map(_.value)).getOrElse(repository.value),
              artifact.version.toString,
              color,
              style,
              logo,
              logoWidth
            )
          case _ =>
            shieldsSvg(
              subject.orElse(artifactName.map(_.value)).getOrElse(repository.value),
              "no published artifact",
              color.orElse(Some("lightgrey")),
              style,
              logo,
              logoWidth
            )

        }
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
        val artifactsF = database.getArtifactsByName(reference, artifactName)
        onSuccess(artifactsF) { artifacts =>
          val availablePlatforms = artifacts.map(_.binaryVersion.platform).distinct
          val platform = platformParam
            .flatMap(Platform.fromLabel)
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
}

object Badges {
  private def summaryOfLatestVersions(artifacts: Seq[Artifact]): String = {
    val versionsByScalaVersions = artifacts
      .groupMap(_.binaryVersion.language)(_.version)
      .collect { case (Scala(v), platform) => Scala(v) -> platform }
    summaryOfLatestVersions(versionsByScalaVersions)
  }

  private[route] def summaryOfLatestVersions(versionsByScalaVersions: Map[Scala, Seq[SemanticVersion]]): String =
    versionsByScalaVersions.view
      .mapValues(_.max)
      .groupMap { case (_, latestVersion) => latestVersion } { case (scalaVersion, _) => scalaVersion }
      .toSeq
      .sortBy(_._1)(SemanticVersion.ordering.reverse)
      .map {
        case (latestVersion, scalaVersions) =>
          val scalaVersionsStr =
            scalaVersions.map(_.version).toSeq.sorted(SemanticVersion.ordering.reverse).mkString(", ")
          s"$latestVersion (Scala $scalaVersionsStr)"
      }
      .mkString(", ")
}
