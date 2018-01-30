package ch.epfl.scala.index
package server
package routes

import model._
import release._
import akka.http.scaladsl._
import server.Directives._
import model.StatusCodes._
import akka.http.scaladsl.server.directives.CachingDirectives.cachingProhibited

class Badges(dataRepository: DataRepository) {

  private val shields = parameters(
    ('color.?, 'style.?, 'logo.?, 'logoWidth.as[Int].?)
  )

  private val shieldsOptionalSubject = shields & parameters('subject.?)
  private val shieldsSubject = shields & parameters('subject)

  private def shieldsSvg(rawSubject: String,
                         rawStatus: String,
                         rawColor: Option[String],
                         style: Option[String],
                         logo: Option[String],
                         logoWidth: Option[Int]) = {

    def shieldEscape(in: String): String =
      in.replaceAllLiterally("-", "--")
        .replaceAllLiterally("_", "__")
        .replaceAllLiterally(" ", "_")

    val subject = shieldEscape(rawSubject)
    val status = shieldEscape(rawStatus)

    val color = rawColor.getOrElse("green")

    // we need a specific encoding
    val query = List(
      style.map(("style", _)),
      logo.map(
        l =>
          ("logo",
           java.net.URLEncoder
             .encode(l, "ascii")
             .replaceAllLiterally("+", "%2B"))
      ),
      logoWidth.map(w => ("logoWidth", w.toString))
    ).flatten.map { case (k, v) => k + "=" + v }.mkString("?", "&", "")

    cachingProhibited {
      redirect(
        s"https://img.shields.io/badge/$subject-$status-$color.svg$query",
        TemporaryRedirect
      )
    }
  }

  def latest(organization: String,
             repository: String,
             artifact: Option[String]) = {
    parameter('target.?) { target =>
      shieldsOptionalSubject { (color, style, logo, logoWidth, subject) =>
        onSuccess(
          dataRepository.projectPage(
            Project.Reference(organization, repository),
            ReleaseSelection.parse(
              target = target,
              artifactName = artifact,
              version = None,
              selected = None
            )
          )
        ) {

          case Some((_, options)) =>
            shieldsSvg(subject orElse artifact getOrElse repository,
                       options.release.reference.version.toString(),
                       color,
                       style,
                       logo,
                       logoWidth)
          case _ =>
            shieldsSvg(subject orElse artifact getOrElse repository,
                       "no published release",
                       color orElse Some("lightgrey"),
                       style,
                       logo,
                       logoWidth)

        }
      }
    }
  }

  val routes =
    get(
      concat(
        pathPrefix(Segment / Segment) { (organization, repository) =>
          concat(
            path(Segment / "latest.svg")(
              artifact => latest(organization, repository, Some(artifact))
            ),
            path("latest.svg")(
              latest(organization, repository, None)
            )
          )
        },
        path("count.svg")(
          parameter('q)(
            query =>
              shieldsSubject(
                (color, style, logo, logoWidth, subject) =>
                  onSuccess(dataRepository.total(query))(
                    count =>
                      shieldsSvg(subject,
                                 count.toString,
                                 color,
                                 style,
                                 logo,
                                 logoWidth)
                )
            )
          )
        )
      )
    )
}
