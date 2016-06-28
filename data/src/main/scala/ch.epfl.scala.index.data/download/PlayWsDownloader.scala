package ch.epfl.scala.index
package data
package download

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import model.misc.Url
import com.typesafe.config.ConfigFactory
import me.tongfei.progressbar.ProgressBar
import play.api.libs.ws.{WSConfigParser, WSRequest, WSResponse}
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient, AhcWSClientConfig}
import play.api.{Configuration, Environment, Mode}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

trait PlayWsDownloader {

  implicit val materializer: ActorMaterializer

  /**
   * Creating a new WS Client - copied from Play website
   *
   * @see https://www.playframework.com/documentation/2.5.x/ScalaWS
   */
  private val wsClient = {

    val configuration = Configuration.reference ++ Configuration(ConfigFactory.parseString(
      """
        |ws.followRedirects = true
      """.stripMargin))

    /** If running in Play, environment should be injected */
    val environment = Environment(new java.io.File("."), this.getClass.getClassLoader, Mode.Prod)

    val parser = new WSConfigParser(configuration, environment)
    val config = new AhcWSClientConfig(wsClientConfig = parser.parse())
    val builder = new AhcConfigBuilder(config)
    val ahcBuilder = builder.configure()

    val ahcConfig = ahcBuilder.build()
    new AhcWSClient(ahcConfig)
  }

  /**
   * Actual download of bunch of documents. Will loop through all and display a status bar in the console output.
   *
   * @param message the message for the loader info
   * @param toDownload the set of downloadable elements
   * @param downloadUrl a function to get the url for the current element
   * @param applyHeaders a function to apply headers to the request
   * @param process a function to process the response in succes case
   * @tparam T Input type
   * @tparam R output type
   */
  def download[T, R](
    message: String,
    toDownload: Set[T],
    downloadUrl: T => Url,
    applyHeaders: WSRequest => WSRequest,
    process: (T, WSResponse) => R
  ): Unit = {

    def processDownloads = {

      Source(toDownload).map { item =>

        val url = downloadUrl(item)
        val response = applyHeaders(wsClient.url(url.target)).get

        response.onComplete {

          case Success(data) => process(item, data)
          case Failure(e) => println(s"error on downloading content from ${url.target}: ${e.getMessage}")
        }

        response
      }
    }

    val progress = new ProgressBar(message, toDownload.size)
    progress.start()
    Await.result(processDownloads.runForeach(_ => progress.step()), Duration.Inf)
    progress.stop()
  }

}
