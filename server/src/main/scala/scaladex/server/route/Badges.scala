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
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.service.WebDatabase
import scaladex.server.BadgesSupport

class Badges(database: WebDatabase)(implicit executionContext: ExecutionContext) {

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
      organization: Project.Organization,
      repository: Project.Repository,
      artifact: Option[Artifact.Name]
  ): RequestContext => Future[RouteResult] =
    parameter("target".?) { platform =>
      shieldsOptionalSubject { (color, style, logo, logoWidth, subject) =>
        val res = getSelectedRelease(
          database,
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
              color.orElse(Some("lightgrey")),
              style,
              logo,
              logoWidth
            )

        }
      }
    }

  def latestByScalaVersion(
      organization: Project.Organization,
      repository: Project.Repository,
      artifact: Artifact.Name
  ): RequestContext => Future[RouteResult] =
    parameter("targetType".?) { targetTypeString =>
      shields { (color, style, logo, logoWidth) =>
        val targetType =
          targetTypeString
            .flatMap(Platform.PlatformType.ofName)
            .getOrElse(Platform.PlatformType.Jvm)
        val res = database.findReleases(
          Project.Reference(organization, repository),
          artifact
        )
        onSuccess {
          res
        } { allAvailableReleases =>
          val notableScalaSupport: String =
            BadgesSupport.summaryOfLatestVersions(
              allAvailableReleases,
              artifact,
              targetType
            )

          shieldsSvg(
            artifact.value,
            notableScalaSupport,
            color,
            style,
            logo,
            logoWidth
          )
        }
      }
    }

  val routes: Route =
    get {
      concat(
        path(organizationM / repositoryM / "latest.svg")((org, repo) => latest(org, repo, None)),
        path(organizationM / repositoryM / artifactM / "latest.svg") { (org, repo, artifact) =>
          latest(org, repo, Some(artifact))
        },
        path(
          organizationM / repositoryM / artifactM / "latest-by-scala-version.svg"
        )((org, repo, artifact) => latestByScalaVersion(org, repo, artifact))
      )
    }
}
