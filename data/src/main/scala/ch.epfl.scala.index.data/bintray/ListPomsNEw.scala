package ch.epfl.scala.index
package data
package bintray

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Flow, Keep, Source}
import akka.util.ByteString
import com.github.nscala_time.time.Imports._
import model._
import download.PlayWsDownloader
import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.libs.ws.ahc.AhcWSClient
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class ListPomsNew(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer)
  extends BintrayProtocol with BintrayCredentials with PlayWsDownloader {

  import system.dispatcher
  /**
   * Internal pagination class
   *
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
   *
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

    request.head.flatMap { response =>

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

    parse(response.body).extract[List[BintraySearch]]
//    parse(response.body.replaceAll("""(\.[0-9]{3,}Z)""", "Z")).extract[List[BintraySearch]]
  }

  def writeToFile(merged: List[BintraySearch]) = {
    Files.delete(bintrayCheckpoint)

    val flow = Flow[BintraySearch]
      .map(bintray => write[BintraySearch](bintray))
      .map(s => ByteString(s + nl))
      .toMat(FileIO.toPath(bintrayCheckpoint))(Keep.right)

    Await.result(Source(merged).runWith(flow), Duration.Inf)
  }

  def run(scalaVersion: String): Unit = {

    println(s"mostRecentQueriedDate: ${mostRecentQueriedDate.map(_.toLocalDateTime.toString)}Z")

    val page: InternalBintrayPagination = Await.result(getNumberOfPages(scalaVersion), Duration.Inf)

    val requestCount = Math.ceil(page.numberOfPages.toDouble / page.itemPerPage.toDouble).toInt
    val toDownload = List.tabulate(requestCount)(p => PomListDownload(scalaVersion, p)).toSet

    val newQueried: Seq[List[BintraySearch]] = download[PomListDownload, List[BintraySearch]]("Download Poms", toDownload, discover, processPomDownload)

    val merged = newQueried.foldLeft(queried)((oldList, newList) => oldList ++ newList).sortBy(_.created)(Descending)

    print("writing Files ... ")
    writeToFile(merged)
    println("done")
    ()
  }
}
