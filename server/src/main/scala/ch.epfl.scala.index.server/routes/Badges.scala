package ch.epfl.scala.index
package server
package routes

import model._
import release._
import akka.http.scaladsl._
import server.Directives._
import model.StatusCodes._

class Badges(dataRepository: DataRepository) {

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

  private def versionBadgeBehavior(organization: String, repository: String, artifact: String, color: Option[String], style: Option[String], logo: Option[String], logoWidth: Option[Int]) = {
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

  private def countBadgeBehavior(query: String, color: Option[String], style: Option[String], logo: Option[String], logoWidth: Option[PageIndex], subject: String) = {
    onSuccess(dataRepository.total(query))(count =>
      shieldsSvg(subject, count.toString, color, style, logo, logoWidth))
  }

  val versionBadge = Routes.versionBadgePath(versionBadgeBehavior)

  val queryCountBadge = Routes.queryBadgePath(countBadgeBehavior)

  val routes = get(versionBadge ~ queryCountBadge)
}