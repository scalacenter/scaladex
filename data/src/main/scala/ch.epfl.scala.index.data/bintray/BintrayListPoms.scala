package ch.epfl.scala.index
package data
package bintray

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Keep, Source}
import akka.util.ByteString
import ch.epfl.scala.index.data.cleanup.NonStandardLib
import ch.epfl.scala.index.data.download.PlayWsDownloader
import ch.epfl.scala.index.model._
import com.github.nscala_time.time.Imports._
import org.typelevel.jawn.support.json4s.Parser
import org.json4s._
import org.json4s.native.Serialization.write
import org.slf4j.LoggerFactory
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class BintrayListPoms private (paths: DataPaths, bintrayClient: BintrayClient)(
    implicit val system: ActorSystem
) extends BintrayProtocol
    with PlayWsDownloader {

  implicit val ec: ExecutionContext = system.dispatcher
  private val log = LoggerFactory.getLogger(getClass)

  import bintrayClient._

  assert(bintrayCredentials.nonEmpty, "this steps requires bintray user")

  /** paginated search query for bintray - append the query string to
   * the request object
   *
   * @param page the page credentials to download
   * @return
   */
  private def discover(wsClient: WSClient, page: PomListDownload): WSRequest = {
    val query = page.lastSearchDate.fold(Seq[(String, String)]())(
      after => Seq("created_after" -> (after.toLocalDateTime.toString + "Z"))
    ) ++ Seq("name" -> s"${page.query}*.pom", "start_pos" -> page.page.toString)

    withAuth(wsClient.url(s"$apiUrl/search/file"))
      .withQueryStringParameters(query: _*)
      .withRequestFilter(AhcCurlRequestLogger())
  }

  /** Fetch bintray first, to find out the pages remaining */
  def getPagination(
      query: String,
      lastCheckDate: Option[DateTime]
  ): Future[InternalBintrayPagination] = {
    val request = discover(client, PomListDownload(query, 0, lastCheckDate))

    request
      .withRequestFilter(AhcCurlRequestLogger())
      .get
      .flatMap { response =>
        if (200 == response.status) {
          Future.successful {
            InternalBintrayPagination(
              BintrayClient.remainingPages(response)
            )
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
  def processSearch(page: PomListDownload,
                    response: WSResponse): List[BintraySearch] = {
    try {
      Parser.parseUnsafe(response.body).extract[List[BintraySearch]]
    } catch {
      case scala.util.control.NonFatal(e) => {
        log.error("failed to parse bintray search", e)
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

    val mavenMetas = Meta.load(paths, LocalPomRepository.MavenCentral)
    val mavenShas = mavenMetas.map(_.sha1).toSet

    val flow = Flow[BintraySearch]
      .filterNot(search => mavenShas.contains(search.sha1))
      .map(bintray => write[BintraySearch](bintray))
      .map(s => ByteString(s + "\n"))
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
      queried
        .find(_.name.contains(scalaVersion))
        .map(search => new DateTime(search.created) - 2.month)

    performSearchAndDownload(s"Bintray: Search POMs for scala $scalaVersion",
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
    def filter(bintray: BintraySearch): Boolean = {
      bintray.path.startsWith(
        groupId.replaceAllLiterally(".", "/") + "/" + artifact
      )
    }

    val mostRecentQueriedDate =
      queried.find(filter).map(search => new DateTime(search.created) - 2.month)

    performSearchAndDownload(s"Bintray: Search Poms for $groupId:$artifact",
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
      log.info(infoMessage)
    }

    def applyFilter(bintray: List[BintraySearch]): List[BintraySearch] = {

      filter match {
        case Some(f) => bintray.filter(f)
        case None    => bintray
      }
    }

    /* get the list of pages */
    val pagination: InternalBintrayPagination =
      Await.result(getPagination(search, mostRecentQueriedDate), Duration.Inf)

    val toDownload = pagination.pages
      .map(
        page => PomListDownload(search, page, mostRecentQueriedDate)
      )
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

    ()
  }
}

object BintrayListPoms {

  /**
   * Use a managed Bintray client to search all the recent Scala poms of the specified
   * scala versions as well as the specified non-standard libs.
   * Then downloads all the poms to the data directories.
   *
   * @param paths The path to the data directories
   * @param scalaVersions The list of desired scala versions
   * @param libs The list of desired non-standard libs
   */
  def run(
      paths: DataPaths,
      scalaVersions: Seq[String],
      libs: Seq[NonStandardLib]
  )(implicit sys: ActorSystem) = {
    for (bintrayClient <- BintrayClient.create(paths.credentials)) {
      val listPoms = new BintrayListPoms(paths, bintrayClient)

      for (scalaVersion <- scalaVersions) {
        listPoms.run(scalaVersion)
      }

      for (lib <- libs) {
        listPoms.run(lib.groupId, lib.artifactId)
      }
    }
  }
}
