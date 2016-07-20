package ch.epfl.scala.index
package data
package bintray

import model.Descending

import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.json4s._
import org.json4s.native.JsonMethods._

import java.nio.file._

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
    created: DateTime
)

/**
  * Internal pagination class
  *
  * @param numberOfPages the maximum number of pages
  * @param itemPerPage the max items per page
  */
case class InternalBintrayPagination(numberOfPages: Int, itemPerPage: Int = 50)

/**
  * Pom list download class to map the version and the scala version for
  * the search query
  *
  * @param scalaVersion the current scala version
  * @param page the current page
  * @param lastSearchDate the last searched date
  */
case class PomListDownload(scalaVersion: String,
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
                      DateTimeSerializer)
                in.extract[BintraySearch]
              }
            }, {
              case search: BintraySearch => {
                implicit val formats = DefaultFormats ++ Seq(
                      DateTimeSerializer)
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
          ))

  /**
    * Scope serializer, since Scope is not a case class json4s can't handle this by default
    *
    */
  object DateTimeSerializer
      extends CustomSerializer[DateTime](
          format =>
            (
                {
              case JString(dateTime) => {
                val parser = ISODateTimeFormat.dateTimeParser
                parser.parseDateTime(dateTime)
              }
            }, {
              case dateTime: DateTime => {
                val formatter = ISODateTimeFormat.dateTime
                JString(formatter.print(dateTime))
              }
            }
          ))
}

object BintrayMeta extends BintrayProtocol {

  /**
    * read all currently downloaded poms and convert them to BintraySearch object
    *
    * @param path the file path
    * @return
    */
  def readQueriedPoms(path: Path): List[BintraySearch] = {
    val source = scala.io.Source.fromFile(path.toFile)
    val ret    = source.mkString.split(nl).toList
    source.close()
    ret
      .filter(_ != "")
      .map(json => parse(json).extract[BintraySearch])
      .sortBy(_.created)(Descending)
  }
}
