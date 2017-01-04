package ch.epfl.scala.index
package server
package routes

import model._
import release._
import akka.http.scaladsl._
import server.Directives._
import model.StatusCodes._

class Badges(dataRepository: DataRepository) {

  private val shields = parameters(('color.?, 'style.?, 'logo.?, 'logoWidth.as[Int].?))
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
      logo.map(l =>
        ("logo", java.net.URLEncoder.encode(l, "ascii").replaceAllLiterally("+", "%2B"))),
      logoWidth.map(w => ("logoWidth", w.toString))
    ).flatten.map { case (k, v) => k + "=" + v }.mkString("?", "&", "")

    redirect(
      s"https://img.shields.io/badge/$subject-$status-$color.svg$query",
      TemporaryRedirect
    )

  }

  val routes =
    get {
      path(Segment / Segment / Segment / "latest.svg") { (organization, repository, artifact) =>
        shields { (color, style, logo, logoWidth) =>
          onSuccess(
            dataRepository.artifactPage(Project.Reference(organization, repository),
                                        ReleaseSelection(Some(artifact), None))) {

            case Some((_, _, release)) =>
              shieldsSvg(artifact,
                         release.reference.version.toString(),
                         color,
                         style,
                         logo,
                         logoWidth)
            case _ =>
              shieldsSvg(artifact,
                         "no published release",
                         color orElse Some("lightgrey"),
                         style,
                         logo,
                         logoWidth)

          }
        }
      } ~
        path("count.svg") {
          parameter('q) { query =>
            shieldsSubject { (color, style, logo, logoWidth, subject) =>
              onSuccess(dataRepository.total(query))(count =>
                shieldsSvg(subject, count.toString, color, style, logo, logoWidth))
            }
          }
        }
    }
}
