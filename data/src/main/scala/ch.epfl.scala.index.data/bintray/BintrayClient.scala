package ch.epfl.scala.index.data
package bintray

import java.net.URL
import java.nio.file.Files

import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods.parse
import org.slf4j.LoggerFactory
import play.api.libs.ws.{WSAuthScheme, WSRequest, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class BintrayClient(paths: DataPaths) {

  val bintrayCredentials = {
    // from bintray-sbt convention
    // cat ~/.bintray/.credentials
    // host = api.bintray.com
    // user = xxxxxxxxxx
    // password = xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

    val credentials = paths.credentials

    if (Files.exists(credentials) && Files.isDirectory(credentials)) {
      val nl = System.lineSeparator
      val source = scala.io.Source.fromFile(
        credentials.resolve("search-credentials").toFile
      )

      val info = source.mkString
        .split(nl)
        .map { v =>
          val (l, r) = v.span(_ != '=')
          (l.trim, r.drop(2).trim)
        }
        .toMap
      source.close()
      info
    } else Map[String, String]()
  }

  private val logger = LoggerFactory.getLogger(this.getClass)

  val bintrayBase: String = "https://bintray.com"

  /** Base URL of Bintray API */
  val bintrayApi: String = s"$bintrayBase/api/v1"

  def withAuth(request: WSRequest): WSRequest = {
    (bintrayCredentials.get("user"), bintrayCredentials.get("password")) match {
      case (Some(user), Some(password)) =>
        request.withAuth(user, password, WSAuthScheme.BASIC)
      case _ => request
    }
  }

  // See https://bintray.com/docs/usermanual/downloads/downloads_downloadingusingapis.html#_overview
  def downloadUrl(subject: String, repo: String, path: String): URL =
    new URL(s"https://dl.bintray.com/$subject/$repo/$path")

  /**
   * Fetches a bintray-paginated resource.
   *
   * @param fetchPage Function that fetches one page given the index of the first element to fetch
   * @param decode    Function that decodes the response into a meaningful list of data
   * @return The whole resource
   */
  def fetchPaginatedResource[A](
      fetchPage: Int => Future[WSResponse]
  )(
      decode: WSResponse => List[A]
  )(implicit ec: ExecutionContext): Future[List[A]] = {
    for {
      // Let’s first get the first page
      firstResponse <- fetchPage(0)
      // And then get the remaining pages, if any
      remainingResponses <- Future.traverse(remainingPages(firstResponse))(
        fetchPage
      )
    } yield {
      // Eventually concatenate all the results together
      remainingResponses.foldLeft(decode(firstResponse)) {
        (results, otherResults) =>
          results ++ decode(otherResults)
      }
    }
  }

  /**
   * @return The list of the remaining queries that have to be performed to get the missing packages
   * @param response Response of the ''first'' query
   */
  def remainingPages(response: WSResponse): Seq[Int] =
    (
      for {
        total <- response
          .header("X-RangeLimit-Total")
          .flatMap(s => Try(s.toInt).toOption)
        count <- response
          .header("X-RangeLimit-EndPos")
          .flatMap(s => Try(s.toInt).toOption)
        if count < total
        remainingPages = (total - 1) / count
      } yield Seq.tabulate(remainingPages)(page => (page + 1) * count)
    ).getOrElse(Nil)

  /**
   * @param response The HTTP response that we want to decode
   * @param decode   The function that decodes the JSON content into a list of meaningful information
   * @return The decoded content. In case of (any) failure, logs the error and returns an empty list.
   */
  def decodeSucessfulJson[A](
      decode: JValue => List[A]
  )(response: WSResponse): List[A] =
    try {
      if (response.status != 200) {
        sys.error(
          s"Got a response with a non-OK status: ${response.statusText} ${response.body}"
        )
      }
      decode(parse(response.body))
    } catch {
      case NonFatal(exn) =>
        logger.error("Unable to decode data", exn)
        Nil
    }
}
