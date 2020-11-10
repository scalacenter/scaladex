package ch.epfl.scala.index
package data
package download

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import play.api._
import play.api.libs.json._
import play.api.libs.ws._
import play.api.libs.ws.ahc.{AhcCurlRequestLogger, _}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.util.Using

trait PlayWsDownloader {

  private val log = LoggerFactory.getLogger(getClass)

  implicit val system: ActorSystem
  import system.dispatcher

  /**
   * Creates a fresh client and closes it after the future returned by `f` completes.
   *
   * {{{
   *   managed { client =>
   *     client.url("http://google.com").get()
   *   }
   * }}}
   */
  def managed[A](f: WSClient => Future[A]): Future[A] = {
    val client = PlayWsClient.open()
    f(client).andThen(_ => client.close())
  }

  /**
   * Actual download of bunch of documents. Will loop through all and display a status bar in the console output.
   *
   * @param message the message for the loader info
   * @param toDownload the set of downloadable elements
   * @param downloadUrl a function to get the WsRequest for the current element
   * @param process a function to process the response in succes case
   * @tparam T Input type
   * @tparam R output type
   */
  def download[T, R](
      message: String,
      toDownload: Set[T],
      downloadUrl: (WSClient, T) => WSRequest,
      process: (T, WSResponse) => R,
      parallelism: Int
  ): Seq[R] = {

    Using.resource(PlayWsClient.open()) { client =>
      val progress = ProgressBar(message, toDownload.size, log)

      def processDownloads = {

        Source(toDownload).mapAsyncUnordered(parallelism) { item =>
          val request =
            downloadUrl(client, item).withRequestFilter(AhcCurlRequestLogger())
          val response = request.get()

          response.transform(
            data => {
              if (toDownload.size > 1) {
                progress.step()
              }
              process(item, data)
            },
            e => {
              log.warn(
                s"error on downloading content from ${request.url}: ${e.getMessage}"
              )

              e
            }
          )
        }
      }

      if (toDownload.size > 1) {
        progress.start()
      }
      val response =
        Await.result(processDownloads.runWith(Sink.seq), Duration.Inf)
      if (toDownload.size > 1) {
        progress.stop()
      }
      response
    }
  }

  /**
   * Actual download of bunch of documents from the Github's REST API. Will loop through all and display a status bar in the console output.
   *
   * @param message the message for the loader info
   * @param toDownload the set of downloadable elements
   * @param downloadUrl a function to get the WsRequest for the current element
   * @param process a function to process the response in succes case
   * @tparam T Input type
   * @tparam R output type
   */
  def downloadGithub[T, R](
      message: String,
      toDownload: Set[T],
      downloadUrl: (WSClient, T) => WSRequest,
      process: (T, WSResponse) => Try[R]
  ): Seq[R] = {

    def processItem(client: WSClient, item: T, progress: ProgressBar) = {
      val request = downloadUrl(client, item)
      val response = request.get()

      response.flatMap { data =>
        if (toDownload.size > 1) {
          progress.step()
        }
        Future.fromTry(process(item, data))
      }

    }

    processDownloads(message, toDownload, processItem)
  }

  /**
   * Actual download of bunch of documents from Github's GraphQL API. Will loop through all and display a status bar in the console output.
   *
   * @param message the message for the loader info
   * @param toDownload the set of downloadable elements
   * @param downloadUrl a function to get the WsRequest for the current element
   * @param query query sent to Github's GraphQL API
   * @param process a function to process the response in succes case
   * @tparam T Input type
   * @tparam R output type
   */
  def downloadGraphql[T, R](
      message: String,
      toDownload: Set[T],
      downloadUrl: WSClient => WSRequest,
      query: T => JsObject,
      process: (T, WSResponse) => Try[R]
  ): Seq[R] = {

    def processItem(client: WSClient, item: T, progress: ProgressBar) = {
      val request = downloadUrl(client)
      val response =
        request.withRequestFilter(AhcCurlRequestLogger()).post(query(item))
      response.flatMap { data =>
        if (toDownload.size > 1) {
          progress.step()
        }
        Future.fromTry(process(item, data))
      }
    }

    processDownloads(message, toDownload, processItem)
  }

  private def processDownloads[T, R](
      message: String,
      toDownload: Set[T],
      processItem: (WSClient, T, ProgressBar) => Future[R]
  ): Seq[R] = {

    def processItems(client: WSClient, progress: ProgressBar) = {
      // use minimal concurrency to avoid abuse rate limit error which is triggered
      // by making too many calls in a short period of time, see https://github.com/scalacenter/scaladex/issues/431
      val parallelism = 4
      Source(toDownload).mapAsyncUnordered(parallelism) { item =>
        processItem(client, item, progress)
      }
    }

    Using.resource(PlayWsClient.open()) { client =>
      val progress = ProgressBar(message, toDownload.size, log)

      if (toDownload.size > 1) {
        progress.start()
      }

      val result = Await.ready(
        processItems(client, progress).runWith(Sink.seq),
        Duration.Inf
      )

      if (toDownload.size > 1) {
        progress.stop()
      }
      // pause for 1s before closing client so other threads that were trying to download
      // don't get interrupted and throw p.s.a.i.n.u.c.D.rejectedExecution if download stopped due to error
      Thread.sleep(1000.toLong)

      result.value
        .map {
          case Success(value) => value
          case Failure(e) =>
            log.warn(s"ERROR - $e")
            Seq()
        }
        .getOrElse(Seq())
    }
  }
}

object PlayWsClient {

  /**
   * Creates a Play Web Service Client.
   * You should avoid using too many WSClients by reusing an open WSClient as much as possible.
   * A good balance is to use one WSClient by targeted web service.
   */
  def open()(implicit mat: Materializer): WSClient = {
    val configuration = Configuration.reference.withFallback(
      Configuration(
        ConfigFactory.parseString("plaw.ws.followRedirects = true")
      )
    )

    /* If running in Play, environment should be injected */
    val environment = Environment(
      new java.io.File("."),
      this.getClass.getClassLoader,
      Mode.Prod
    )

    val wsConfig = AhcWSClientConfigFactory.forConfig(
      configuration.underlying,
      environment.classLoader
    )

    AhcWSClient(wsConfig)
  }
}
