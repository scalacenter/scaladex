package ch.epfl.scala.index.data.bintray

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.download.PlayWsDownloader
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.libs.ws.ahc.AhcWSClient
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ListPomsNew(implicit system: ActorSystem, implicit val materializer: ActorMaterializer)
  extends BintrayProtocol with BintrayCredentials with PlayWsDownloader {

  /**
   * Internal pagination class
   * @param numberOfPages the maximum number of pages
   * @param itemPerPage the max items per page
   */
  case class InternalBintrayPagination(numberOfPages: Int, itemPerPage: Int)

  case class PomListDownload(scalaVersion: String, page: Int)
  /**
   * List of already fetched poms
   */
  val queried = BintrayMeta.sortedByCreated(bintrayCheckpoint)
  var newQueried: List[BintraySearch] = List()
  val binTrayUri = "https://bintray.com/api/v1/search/file"
  val mostRecentQueriedDate = queried.headOption.map(_.created)

  /** paginated search query for bintray - append the query string to
   * the request object
   * @param client The play ws client
   * @param page the page credentials to download
   * @return
   */
  private def discover(client: AhcWSClient, page: PomListDownload): WSRequest = {

    val query = mostRecentQueriedDate.fold(Seq[(String, String)]())(after =>

      Seq("created_after" -> (after.toLocalDateTime.toString + "Z"))
    ) ++ Seq("name" -> s"*_${page.scalaVersion}*.pom", "start_pos" -> page.page.toString)

    withAuth(client.url(binTrayUri)).withQueryString(query: _*)
  }

  /** Fetch bintray first, to find out the number of pages and items to iterate
   * them over
   *
   * @param scalaVersion the current scala version
   * @return
   */
  def getNumberOfPages(scalaVersion: String): Future[InternalBintrayPagination] = {

    val request = discover(wsClient, PomListDownload(scalaVersion, 0))

    request.get.flatMap { response =>

      response.status match {

        case 200 => Future.successful{

          (response.header("X-RangeLimit-Total"), response.header("X-RangeLimit-EndPos")) match {

            case (Some(totalPages), Some(limit)) => InternalBintrayPagination(totalPages.toInt, limit.toInt)
            case (Some(totalPages), None) => InternalBintrayPagination(totalPages.toInt, 50)
            case _ => InternalBintrayPagination(0, 50)
          }
        }
        case _ => Future.failed(new Exception(response.statusText))
      }
    }
  }

  def processPomDownload(page: PomListDownload, response: WSResponse): List[BintraySearch] = {

//    println(response.body)

    parse(response.body).extract[List[BintraySearch]]
  }

  def run(scalaVersion: String) = {

    println(s"mostRecentQueriedDate: ${mostRecentQueriedDate.map(_.toLocalDateTime.toString)}Z")

//    val pagination = getNumberOfPages(scalaVersion)
//    pagination.onFailure { case ex: Throwable => println(ex.getMessage)}

//    pagination map { page =>

//      println(page)
//      val requestCount = Math.floor(page.numberOfPages.toDouble / page.numberOfPages.toDouble).toInt
      val toDownload = List.tabulate(1)(p => PomListDownload(scalaVersion, p)).toSet

      println(toDownload)
      val newQueried = download[PomListDownload, List[BintraySearch]]("Download Poms", toDownload, discover, processPomDownload)

      println(newQueried)
//    }
  }
}
