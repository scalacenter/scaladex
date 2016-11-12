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
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.WSAuthScheme

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class BintrayListPoms(paths: DataPaths)(implicit val system: ActorSystem,
                                        implicit val materializer: ActorMaterializer)
    extends BintrayProtocol
    with PlayWsDownloader {

  import system.dispatcher

  /**
    * The url to search at
    */
  private val bintrayUri = "https://bintray.com/api/v1/search/file"

  private val bintrayCredentials = {
    // from bintray-sbt convention
    // cat ~/.bintray/.credentials
    // host = api.bintray.com
    // user = xxxxxxxxxx
    // password = xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

    val home   = System.getProperty("user.home")
    val path   = home + "/.bintray/.credentials2"
    val nl     = System.lineSeparator
    val source = scala.io.Source.fromFile(path)

    val info = source.mkString
      .split(nl)
      .map { v =>
        val (l, r) = v.span(_ != '=')
        (l.trim, r.drop(2).trim)
      }
      .toMap
    source.close()

    info
  }

  def withAuth(request: WSRequest) = {
    (bintrayCredentials.get("user"), bintrayCredentials.get("password")) match {
      case (Some(user), Some(password)) =>
        request.withAuth(user, password, WSAuthScheme.BASIC)
      case _ => request
    }
  }

  /** paginated search query for bintray - append the query string to
    * the request object
    *
    * @param page the page credentials to download
    * @return
    */
  private def discover(wsClient: AhcWSClient, page: PomListDownload): WSRequest = {
    val query = page.lastSearchDate.fold(Seq[(String, String)]())(after =>
        Seq("created_after" -> (after.toLocalDateTime.toString + "Z"))) ++ Seq(
        "name" -> s"${page.scalaVersion}*.pom",
        "start_pos" -> page.page.toString)

    withAuth(wsClient.url(bintrayUri)).withQueryString(query: _*)
  }

  /** Fetch bintray first, to find out the number of pages and items to iterate
    * them over
    *
    * @param scalaVersion the current scala version
    * @return
    */
  def getNumberOfPages(scalaVersion: String,
                       lastCheckDate: Option[DateTime]): Future[InternalBintrayPagination] = {
    val client = wsClient
    val request = discover(client, PomListDownload(scalaVersion, 0, lastCheckDate))

    request.get.flatMap { response =>
      if (200 == response.status) {
        Future.successful {
          InternalBintrayPagination(
            response.header("X-RangeLimit-Total").map(_.toInt).getOrElse(0))
        }
      } else {
        Future.failed(new Exception(response.statusText))
      }
    }.map(v => { client.close(); v })
  }

  /**
    * Convert the json response to BintraySearch class
    *
    * @param page the current page object
    * @param response the current response
    * @return
    */
  def processSearch(page: PomListDownload, response: WSResponse): List[BintraySearch] = {
    try {
      parse(response.body).extract[List[BintraySearch]]
    } catch {
      case scala.util.control.NonFatal(e) => {
        println(e)
        List()
      }
    }
  }

  /**
    * write the list of BintraySerch classes back to a file
    *
    * @param merged the merged list
    * @return
    */
  def writeMergedPoms(merged: List[BintraySearch]) = {
    Files.delete(BintrayMeta.path(paths))

    val flow = Flow[BintraySearch]
      .map(bintray => write[BintraySearch](bintray))
      .map(s => ByteString(s + nl))
      .toMat(FileIO.toPath(BintrayMeta.path(paths)))(Keep.right)

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

    val queried = BintrayMeta.load(paths)

    val mostRecentQueriedDate =
      queried.find(_.name.contains(scalaVersion)).map(_.created - 2.month)

    performSearchAndDownload(s"List POMs for scala $scalaVersion",
                             queried,
                             s"*_$scalaVersion",
                             mostRecentQueriedDate)
  }

  /**
    * search for non standard published artifacts and apply a filter later to make sure that
    * the page name is identical.
    * @param groupId the current group id
    * @param artifact the artifact name
    */
  def run(groupId: String, artifact: String): Unit = {

    val queried = BintrayMeta.load(paths)

    /* the filter to make sure only this artifact get's added */
    def filter(bintray: BintraySearch): Boolean =
      bintray.`package` == s"$groupId:$artifact"

    val mostRecentQueriedDate = queried.find(filter).map(_.created - 2.month)

    performSearchAndDownload(s"List Poms for $groupId:$artifact",
                             queried,
                             artifact,
                             mostRecentQueriedDate,
                             Some(filter))
  }

  /**
    * do the actual search on bintray for files
    * @param infoMessage the message to display for downloading
    * @param queried the list of currently fetched searches
    * @param search the search string
    * @param mostRecentQueriedDate the last fetched date
    * @param filter an optional filter, to filter the response before adding
    */
  def performSearchAndDownload(
      infoMessage: String,
      queried: List[BintraySearch],
      search: String,
      mostRecentQueriedDate: Option[DateTime],
      filter: Option[BintraySearch => Boolean] = None
  ) = {

    if (queried.size == 1) {
      println(infoMessage)
    }

    def applyFilter(bintray: List[BintraySearch]): List[BintraySearch] = {

      filter match {
        case Some(f) => bintray.filter(f)
        case None => bintray
      }
    }

    /* check first how many pages there are */
    val page: InternalBintrayPagination =
      Await.result(getNumberOfPages(search, mostRecentQueriedDate), Duration.Inf)

    val requestCount = Math.ceil(page.numberOfPages.toDouble / page.itemPerPage.toDouble).toInt

    if (0 < requestCount) {

      val toDownload = List
        .tabulate(requestCount)(p =>
          PomListDownload(search, p * page.itemPerPage + page.itemPerPage, mostRecentQueriedDate))
        .toSet

      /* fetch all data from bintray */
      val newQueried: Seq[List[BintraySearch]] =
        download[PomListDownload, List[BintraySearch]](infoMessage,
                                                       toDownload,
                                                       discover,
                                                       processSearch,
                                                       parallelism = 1)

      /* maybe we have here a problem with duplicated poms */
      val merged = newQueried
        .foldLeft(queried)((oldList, newList) => oldList ++ applyFilter(newList))
        .distinct
        .sortBy(_.created)(Descending)

      writeMergedPoms(merged)

    } else {

      println("no new files found ... continue")
    }

    ()
  }
}
