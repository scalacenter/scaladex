package scaladex.core.util

import scala.jdk.CollectionConverters._

import org.jsoup.Jsoup

object JsoupUtils {
  def listDirectories(url: String, page: String): Seq[String] =
    listElements(url, page)
      .filter(_.endsWith("/"))
      .map(s => s.substring(0, s.length - 1))

  def listFiles(url: String, page: String): Seq[String] =
    listElements(url, page)
      .filter(!_.endsWith("/"))

  private def listElements(url: String, page: String): Seq[String] =
    listWebPageLinks(page)
      .collect {
        case elem if elem.nonEmpty =>
          elem
            .stripPrefix(url)
      }
      .filter { n =>
        lazy val slashIdx = n.indexOf('/')
        n != "./" && n != "../" && n != "." && n != ".." && n != "/" && (slashIdx < 0 || slashIdx == n.length - 1)
      }

  private def listWebPageLinks(page: String): Seq[String] =
    Jsoup
      .parse(page)
      .select("a")
      .asScala
      .toSeq
      .map(_.attr("href"))
}
