package ch.epfl.scala.index
package data
package bintray

import model._
import download.PlayWsDownloader

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Flow, Keep, Source}
import akka.util.ByteString

import com.github.nscala_time.time.Imports._

import play.api.libs.ws.{WSRequest, WSResponse}

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class ListPoms(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer)
  extends BintrayProtocol with BintrayCredentials with PlayWsDownloader {

  import system.dispatcher

  /**
   * The url to search at
   */
  val binTrayUri = "https://bintray.com/api/v1/search/file"

  /** paginated search query for bintray - append the query string to
   * the request object
   *
   * @param page the page credentials to download
   * @return
   */
  private def discover(page: PomListDownload): WSRequest = {

    val query = page.lastSearchDate.fold(Seq[(String, String)]())(after =>

      Seq("created_after" -> (after.toLocalDateTime.toString + "Z"))
    ) ++ Seq("name" -> s"*_${page.scalaVersion}*.pom", "start_pos" -> page.page.toString)

    withAuth(wsClient.url(binTrayUri)).withQueryString(query: _*)
  }

  /** Fetch bintray first, to find out the number of pages and items to iterate
   * them over
   *
   * @param scalaVersion the current scala version
   * @return
   */
  def getNumberOfPages(scalaVersion: String, lastCheckDate: Option[DateTime]): Future[InternalBintrayPagination] = {

    val request = discover(PomListDownload(scalaVersion, 0, lastCheckDate))

    request.get.flatMap { response =>

      if (200 == response.status) {
       Future.successful{

          (response.header("X-RangeLimit-Total"), response.header("X-RangeLimit-EndPos")) match {

            case (Some(totalPages), Some(limit)) => InternalBintrayPagination(totalPages.toInt, limit.toInt)
            case (Some(totalPages), None) => InternalBintrayPagination(totalPages.toInt, 50)
            case _ => InternalBintrayPagination(0, 50)
          }
        }
      } else {

        Future.failed(new Exception(response.statusText))
      }
    }
  }

  /**
   * Convert the json response to BintraySearch class
   *
   * @param page the current page object
   * @param response the current response
   * @return
   */
  def processPomDownload(page: PomListDownload, response: WSResponse): List[BintraySearch] = {

    parse(response.body).extract[List[BintraySearch]]
  }

  /**
   * write the list of BintraySerch classes back to a file
   *
   * @param merged the merged list
   * @return
   */
  def writeMergedPoms(merged: List[BintraySearch]) = {

    Files.delete(bintrayCheckpoint)

    val flow = Flow[BintraySearch]
      .map(bintray => write[BintraySearch](bintray))
      .map(s => ByteString(s + nl))
      .toMat(FileIO.toPath(bintrayCheckpoint))(Keep.right)

    Await.result(Source(merged).runWith(flow), Duration.Inf)
  }

  /**
   * run task to:
   * - read current downloaded poms
   * - check how many pages there are for the search
   * - fetch all pages
   * - merge current Search results with new results
   * - write them back to file
   *
   * @param scalaVersion the scala version to search for new artifacts
   */
  def run(scalaVersion: String): Unit = {

    val queried = BintrayMeta.readQueriedPoms(bintrayCheckpoint)

    val mostRecentQueriedDate = queried.find(_.name.contains(scalaVersion))map(_.created)
    println(s"mostRecentQueriedDate: ${mostRecentQueriedDate.getOrElse("None")}")

    /* check first how many pages there are */
    val page: InternalBintrayPagination = Await.result(getNumberOfPages(scalaVersion, mostRecentQueriedDate), Duration.Inf)

    val requestCount = Math.ceil(page.numberOfPages.toDouble / page.itemPerPage.toDouble).toInt

    if (0 < requestCount) {

      val toDownload = List.tabulate(requestCount)(p => PomListDownload(scalaVersion, p, mostRecentQueriedDate)).toSet

      /* fetch all data from bintray */
      val newQueried: Seq[List[BintraySearch]] = download[PomListDownload, List[BintraySearch]]("Download Poms", toDownload, discover, processPomDownload)

      /* maybe we have here a problem with dublicated poms */
      val merged = newQueried.foldLeft(queried)((oldList, newList) => oldList ++ newList).sortBy(_.created)(Descending)

      print("writing Files ... ")
      writeMergedPoms(merged)
      println("done")
    } else {

      println("no new files found ... continue")
    }

    ()
  }
}
