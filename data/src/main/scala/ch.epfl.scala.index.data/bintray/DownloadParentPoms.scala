package ch.epfl.scala.index
package data
package bintray

import download.PlayWsDownloader
import maven._

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.libs.ws.ahc.AhcWSClient

import scala.util.Failure

class DownloadParentPoms(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer)
    extends PlayWsDownloader {

  /**
    * get the play-ws request by using the dependency
    * @param dep the current depenency
    * @return
    */
  def downloadRequest(wsClient: AhcWSClient, dep: Dependency): WSRequest = {
    wsClient.url(s"https://repo.jfrog.org/artifactory/libs-release-bintray/${PomsReader.path(dep)}")
  }

  /**
    * process the HTTP response - save the file to disk it status is 200 (OK)
    * otherwise return 1 for failed download
    *
    * @param dep the current dependency
    * @param response the current response
    * @return
    */
  def processResponse(dep: Dependency, response: WSResponse): Int = {

    if (200 == response.status) {

      val pomPath = PomsReader.parentPomsBase.resolve(PomsReader.path(dep))
      Files.createDirectories(pomPath.getParent)

      val printer = new scala.xml.PrettyPrinter(80, 2)
      val pw      = new java.io.PrintWriter(pomPath.toFile)

      pw.println(printer.format(scala.xml.XML.loadString(response.body)))
      pw.close()

      0
    } else 1
  }

  /**
    * do the main run
    *
    * @param lastFailedToDownload the number of last failed downloads
    */
  def run(lastFailedToDownload: Int = 0): Unit = {

    /* load poms */
    val parentPomsToDownload: Set[Dependency] = PomsReader
      .load()
      .collect {

        case Failure(m: MissingParentPom) => m.dep
      }
      .toSet

    println(s"to download: ${parentPomsToDownload.size}")
    println(s"last failed: $lastFailedToDownload")

    if (parentPomsToDownload.size > lastFailedToDownload) {

      val downloaded =
        download[Dependency, Int]("Download parent POMs", parentPomsToDownload, downloadRequest, processResponse)
      val failedDownloads = downloaded.sum

      println(s"failed downloads: $failedDownloads")

      if (0 < failedDownloads && parentPomsToDownload.size != failedDownloads) {

        run(failedDownloads) // grand-parent poms, etc
      }
    }
  }
}
