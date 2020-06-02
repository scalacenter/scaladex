package ch.epfl.scala.index.data
package bintray

import download.PlayWsDownloader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.libs.ws.ahc.AhcWSClient
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.slf4j.LoggerFactory

class BintrayDownloadPoms(paths: DataPaths)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer
) extends PlayWsDownloader {

  private val log = LoggerFactory.getLogger(getClass)

  private val bintrayPomBase = paths.poms(LocalPomRepository.Bintray)

  /**
   * resolve the filename for a specific pom by sha1
   *
   * @param search the bintray search object
   * @return
   */
  private def pomPath(search: BintraySearch) =
    bintrayPomBase.resolve(s"${search.sha1}.pom")

  /**
   * will compute ans sha1 hash from given string and verify that against
   * given compare hash
   * @param toVerify the string to compute
   * @param sha1 the verify hash
   * @return
   */
  def verifyChecksum(toVerify: String, sha1: String) = {

    val md = java.security.MessageDigest.getInstance("SHA-1")
    val computed =
      md.digest(toVerify.getBytes("UTF-8")).map("%02x".format(_)).mkString

    computed == sha1
  }

  /**
   * verify the sha1 file hash
   * @param path the path to the file to verify
   * @param sha1 the sha1 hash
   * @return
   */
  private def verifySHA1FileHash(path: Path, sha1: String): Boolean = {

    val source = scala.io.Source.fromFile(path.toFile)
    val content = source.mkString
    source.close()

    verifyChecksum(content, sha1)
  }

  /**
   * get a list of bintraySearch object where no sha1 file exists
   */
  private val searchesBySha1: Set[BintraySearch] = {

    BintrayMeta
      .load(paths)
      .filter(
        s =>
          !Files.exists(pomPath(s)) || !verifySHA1FileHash(pomPath(s), s.sha1)
      )
      .groupBy(_.sha1) // remove duplicates with sha1
      .map { case (_, vs) => vs.head }
      .toSet
  }

  /**
   * partly url encode - replaces only spaces
   *
   * @param url the url to "encode"
   * @return
   */
  private def escape(url: String) = url.replace(" ", "%20")

  /**
   * get the download request object
   * downloads pom from
   * - jcenter.bintray.com or
   * - dl.bintray.com
   *
   * @param search the bintray Search
   * @return
   */
  private def downloadRequest(wsClient: WSClient,
                              search: BintraySearch): WSRequest = {

    if (search.isJCenter) {
      wsClient.url(escape(s"https://jcenter.bintray.com/${search.path}"))
    } else {
      wsClient.url(
        escape(
          s"https://dl.bintray.com/${search.owner}/${search.repo}/${search.path}"
        )
      )
    }
  }

  /**
   * handle the downloaded pom and write it to file
   * @param search the bintray search
   * @param response the download response
   */
  private def processPomDownload(search: BintraySearch,
                                 response: WSResponse): Unit = {

    if (200 == response.status) {

      val path = pomPath(search)

      if (Files.exists(path)) {

        Files.delete(path)
      }

      if (verifyChecksum(response.body, search.sha1)) {

        Files.write(path, response.body.getBytes(StandardCharsets.UTF_8))
      }

      ()
    } else {

      log.warn(
        "Pom download failed\n" +
          search.toString + "\n" +
          response.body.toString
      )
    }
  }

  /**
   * main run method
   */
  def run(): Unit = {

    download[BintraySearch, Unit]("Downloading POMs",
                                  searchesBySha1,
                                  downloadRequest,
                                  processPomDownload,
                                  parallelism = 32)
    ()
  }
}
