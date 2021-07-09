package ch.epfl.scala.index.data
package bintray

import java.io.Closeable
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import akka.actor.ActorSystem
import ch.epfl.scala.index.data.download.PlayWsClient
import org.json4s.JsonAST.JValue
import org.typelevel.jawn.support.json4s.Parser
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSResponse

/**
 * [[BintrayClient]] allows to query the Bintray REST API (https://bintray.com/docs/api/)
 * A Bintray Client encapsulates a WSClient that must be closed after usage.
 *
 * @param credentials Path to the Bintray credentials file
 * @param client A Play Web Service client that is used internally to communicate with the Bintray REST API
 * @param ec
 */
class BintrayClient private (
    credentials: Path,
    val client: WSClient // TODO should be private
)(implicit ec: ExecutionContext)
    extends BintrayProtocol
    with Closeable {
  import BintrayClient._

  val bintrayCredentials: Map[String, String] = {
    // from bintray-sbt convention
    // cat ~/.bintray/.credentials
    // host = api.bintray.com
    // user = xxxxxxxxxx
    // password = xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

    if (Files.exists(credentials) && Files.isDirectory(credentials)) {
      val source = scala.io.Source.fromFile(
        credentials.resolve("search-credentials").toFile
      )

      val info = source.mkString
        .split('\n')
        .map { v =>
          val (l, r) = v.span(_ != '=')
          (l.trim, r.drop(2).trim)
        }
        .toMap
      source.close()
      info
    } else Map[String, String]()
  }

  val bintrayBase: String = "https://bintray.com"

  /** Base URL of Bintray API */
  val apiUrl: String = s"$bintrayBase/api/v1"

  /**
   * Get a list of packages in the specified repository
   * https://bintray.com/docs/api/#_get_packages
   */
  def getAllPackages(subject: String, repo: String): Future[Seq[String]] = {
    def getPage(page: Int) = {
      val request = client
        .url(s"$apiUrl/repos/$subject/$repo/packages")
        .withQueryStringParameters("start_pos" -> page.toString)

      withAuth(request).get()
    }

    val decodePackages = decodeSucessfulJson { json =>
      json.children.map(child => (child \ "name").extract[String])
    } _

    fetchPaginatedResource(getPage)(decodePackages)
  }

  /**
   * Get general information about a specified package with package name.
   * https://bintray.com/docs/api/#_get_package
   */
  def getPackage(
      subject: String,
      repo: String,
      packageName: String
  ): Future[BintrayPackage] = {
    val request = client.url(s"$apiUrl/packages/$subject/$repo/$packageName")

    withAuth(request).get().map {
      decodeSucessfulJson { json =>
        json.extract[BintrayPackage]
      }
    }
  }

  /**
   * Search for a file by its name in the entire repository
   * https://bintray.com/docs/api/#_file_search_by_name
   *
   * @param subject
   * @param repo
   * @param fileName The file name, which can take the * and ? wildcard characters
   * @param createdAfter A date following ISO8601 format (yyyy-MM-dd’T’HH:mm:ss.SSSZ)
   * @return the list of files
   */
  def searchFiles(
      subject: String,
      repo: String,
      fileName: String,
      createdAfter: String
  ): Future[Seq[BintraySearch]] = {
    def getPage(page: Int) = {
      val request = client
        .url(s"$apiUrl/search/file")
        .withQueryStringParameters(
          "name" -> fileName,
          "subject" -> subject,
          "repo" -> repo,
          "created_after" -> createdAfter,
          "start_pos" -> page.toString
        )

      withAuth(request).get()
    }

    val decodeFile = decodeSucessfulJson { json =>
      json.children.map(_.extract[BintraySearch])
    } _

    fetchPaginatedResource(getPage)(decodeFile)
  }

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
      decode: WSResponse => Seq[A]
  )(implicit ec: ExecutionContext): Future[Seq[A]] = {
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
   * @param response The HTTP response that we want to decode
   * @param decode   The function that decodes the JSON content into a list of meaningful information
   * @return The decoded content. In case of (any) failure, logs the error and returns an empty list.
   */
  def decodeSucessfulJson[A](decode: JValue => A)(response: WSResponse): A = {
    if (response.status != 200) {
      sys.error(
        s"Got a response with a non-OK status: ${response.statusText} ${response.body}"
      )
    }
    decode(Parser.parseUnsafe(response.body))
  }

  def close(): Unit = client.close()
}

object BintrayClient {

  /**
   * Creates a BintrayClient that must be closed after usage.
   *
   * @param credentials Path to the Bintray credentials file
   * @return
   */
  def create(credentials: Path)(implicit
      sys: ActorSystem
  ): BintrayClient = {
    val client = PlayWsClient.open()
    new BintrayClient(credentials, client)(sys.dispatcher)
  }

  /**
   * @return The list of the remaining queries that have to be performed to get the missing packages
   * @param response Response of the ''first'' query
   */
  def remainingPages(response: WSResponse): Seq[Int] = {
    val remainingPages = for {
      total <- response
        .header("X-RangeLimit-Total")
        .flatMap(s => Try(s.toInt).toOption)
      startPos <- response
        .header("X-RangeLimit-StartPos")
        .flatMap(s => Try(s.toInt).toOption)
      endPos <- response
        .header("X-RangeLimit-EndPos")
        .flatMap(s => Try(s.toInt).toOption)
      if endPos < (total - 1)
      nextPos = endPos + 1
      perPage = nextPos - startPos
      remainingPageCount = Math
        .ceil((total - nextPos).toDouble / perPage)
        .toInt
    } yield Seq.tabulate(remainingPageCount)(page => nextPos + page * perPage)
    remainingPages.getOrElse(Seq())
  }
}
