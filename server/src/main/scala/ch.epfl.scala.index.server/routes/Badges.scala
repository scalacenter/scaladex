package ch.epfl.scala.index
package server
package routes

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.services.DatabaseApi

class Badges(db: DatabaseApi)(implicit
    executionContext: ExecutionContext
) {

  private val shields = parameters(
    ("color".?, "style".?, "logo".?, "logoWidth".as[Int].?)
  )

  private val shieldsOptionalSubject = shields & parameters("subject".?)
  private val shieldsSubject = shields & parameters("subject")

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
      organization: NewProject.Organization,
      repository: NewProject.Repository,
      artifact: Option[NewRelease.ArtifactName]
  ): RequestContext => Future[RouteResult] = {
    parameter("target".?) { platform =>
      shieldsOptionalSubject { (color, style, logo, logoWidth, subject) =>
        val res = getSelectedRelease(
          db,
          organization,
          repository,
          platform,
          artifact,
          version = None,
          selected = None
        )
        onSuccess(res) {
          case Some(release) =>
            shieldsSvg(
              subject.orElse(artifact.map(_.value)).getOrElse(repository.value),
              release.version.toString,
              color,
              style,
              logo,
              logoWidth
            )
          case _ =>
            shieldsSvg(
              subject.orElse(artifact.map(_.value)).getOrElse(repository.value),
              "no published release",
              color orElse Some("lightgrey"),
              style,
              logo,
              logoWidth
            )

        }
      }
    }
  }

  private def javaBadge(rel: NewRelease): String =
    s"Java: ${rel.version.toString()}"

  private def shieldForPlatform(
      platform: Platform.PlatformType,
      org: NewProject.Organization,
      repo: NewProject.Repository,
      art: NewRelease.ArtifactName
  ) =
    shields { (color, style, logo, logoWidth) =>
      onSuccess(
        db.findReleases(Project.Reference(org.value, repo.value), art)
      ) { allReleases =>
        if (platform == Platform.Java) {
          shieldsSvg(
            javaBadge(allReleases.maxBy(_.version)),
            "",
            color,
            style,
            logo,
            logoWidth
          )
        } else {
          val (pt, pv, scalaToSem) =
            BadgeTools.mostRecentByScalaVersionAndPlatVersion(platform)(
              allReleases
            )
          val rawVersions = scalaToSem
            .groupBy(_._2)
            .map { case (ver, list) =>
              s"$ver (${list.unzip._1.map(scalaV => s"Scala $scalaV").mkString(", ")})"
            }
            .mkString("; ")
          val content = s"$pt${pv.map(" " + _).getOrElse("")} | $rawVersions"
          shieldsSvg(
            content,
            "",
            color,
            style,
            logo,
            logoWidth
          )
        }
      }
    }

  private def shieldForPlatformWithVersion(
      platform: Platform.PlatformType,
      platformVersion: BinaryVersion,
      org: NewProject.Organization,
      repo: NewProject.Repository,
      art: NewRelease.ArtifactName
  ) = {
    shields { (color, style, logo, logoWidth) =>
      onSuccess(
        db.findReleases(Project.Reference(org.value, repo.value), art)
      ) { allReleases =>
        if (platform == Platform.Java) {
          shieldsSvg(
            javaBadge(allReleases.maxBy(_.version)),
            "",
            color,
            style,
            logo,
            logoWidth
          )
        } else {
          val badge: List[(String, SemanticVersion)] =
            BadgeTools.mostRecentByScalaVersion(
              platform,
              Some(platformVersion)
            )(
              allReleases
            )
          val rawBadge = badge
            .groupBy(_._2)
            .map { case (ver, list: List[(String, SemanticVersion)]) =>
              s"$ver (${list.unzip._1.map(scalaV => s"Scala $scalaV").mkString(", ")})"
            }
            .mkString(" - ")
          shieldsSvg(rawBadge, "", color, style, logo, logoWidth)
        }
      }
    }
  }

  def newBadgeRoute(
      org: NewProject.Organization,
      repo: NewProject.Repository,
      art: NewRelease.ArtifactName
  ): RequestContext => Future[RouteResult] =
    parameters("targetType", "platformVersion".?) {
      (rawPlatform, maybePlatformVers) =>
        (
          Platform.PlatformType.ofName(rawPlatform),
          maybePlatformVers.flatMap(BinaryVersion.parse)
        ) match {
          case (Some(platform), Some(platformVersion)) =>
            shieldForPlatformWithVersion(platform, platformVersion, org, repo, art)
          case (Some(platform), None) => shieldForPlatform(platform, org, repo, art)
          case _ => shieldForPlatform(Platform.PlatformType.Jvm, org, repo, art)
        }
    }

  val routes: Route =
    get {
      concat(
        path(organizationM / repositoryM / "latest.svg") { (org, repo) =>
          latest(org, repo, None)
        },
        path(organizationM / repositoryM / artifactM / "latest.svg") {
          (org, repo, artifact) =>
            latest(org, repo, Some(artifact))
        },
        path(organizationM / repositoryM / artifactM / "on") {
          (org, repo, artifact) =>
            newBadgeRoute(org, repo, artifact)
        }
      )
    }
}
