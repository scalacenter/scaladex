package ch.epfl.scala.index
package data
package bintray

import org.json4s._
import org.joda.time.DateTime

case class BintraySearch(
    sha1: String,
    sha256: Option[String],
    `package`: String,
    name: String,
    path: String,
    size: Int,
    version: String,
    owner: String,
    repo: String,
    created: String
) {
  def isJCenter = repo == "jcenter" && owner == "bintray"
}

/**
 * Internal pagination class
 *
 * @param pages list of the pages number
 */
case class InternalBintrayPagination(pages: Seq[Int])

/**
 * Pom list download class to map the version and the scala version for
 * the search query
 *
 * @param query
 * @param page the current page
 * @param lastSearchDate the last searched date
 */
case class PomListDownload(query: String,
                           page: Int,
                           lastSearchDate: Option[DateTime])

/**
 * Bintray protocol
 */
trait BintrayProtocol {

  /**
   * json4s formats
   */
  implicit val formats = DefaultFormats ++ Seq(DateTimeSerializer,
                                               BintraySearchSerializer)
  implicit val serialization = native.Serialization

  /**
   * BintraySearchSerializer to keep the fields ordering
   */
  object BintraySearchSerializer
      extends CustomSerializer[BintraySearch](
        format =>
          (
            {
              case in: JValue => {
                implicit val formats = DefaultFormats ++ Seq(
                  DateTimeSerializer
                )
                in.extract[BintraySearch]
              }
            }, {
              case search: BintraySearch => {
                implicit val formats = DefaultFormats ++ Seq(
                  DateTimeSerializer
                )
                JObject(
                  JField("created", Extraction.decompose(search.created)),
                  JField("package", Extraction.decompose(search.`package`)),
                  JField("owner", Extraction.decompose(search.owner)),
                  JField("repo", Extraction.decompose(search.repo)),
                  JField("sha1", Extraction.decompose(search.sha1)),
                  JField("sha256", Extraction.decompose(search.sha256)),
                  JField("name", Extraction.decompose(search.name)),
                  JField("path", Extraction.decompose(search.path)),
                  JField("size", Extraction.decompose(search.size)),
                  JField("version", Extraction.decompose(search.version))
                )
              }
            }
        )
      )
}
