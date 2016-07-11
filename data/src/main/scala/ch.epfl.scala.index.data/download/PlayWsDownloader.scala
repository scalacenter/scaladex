package ch.epfl.scala.index
package data
package download

import me.tongfei.progressbar.ProgressBar

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import com.typesafe.config.ConfigFactory

import play.api.libs.ws.{WSConfigParser, WSRequest, WSResponse}
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient, AhcWSClientConfig}
import play.api.{Configuration, Environment, Mode}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait PlayWsDownloader {

  implicit val system: ActorSystem
  import system.dispatcher
  implicit val materializer: ActorMaterializer

  /**
   * Creating a new WS Client - copied from Play website
   *
   * @see https://www.playframework.com/documentation/2.5.x/ScalaWS
   */
  def wsClient = {

    val configuration = Configuration.reference ++ Configuration(ConfigFactory.parseString(
      """
        |ws.followRedirects = true
      """.stripMargin))

    /* If running in Play, environment should be injected */
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
   * @param downloadUrl a function to get the WsRequest for the current element
   * @param process a function to process the response in succes case
   * @tparam T Input type
   * @tparam R output type
   */
  def download[T, R](
    message: String,
    toDownload: Set[T],
    downloadUrl: (AhcWSClient, T) => WSRequest,
    process: (T, WSResponse) => R
  ): Seq[R] = {

    val client = wsClient
    val progress = new ProgressBar(message, toDownload.size)

    def processDownloads = {

      Source(toDownload).mapAsync(32) { item =>

        val request = downloadUrl(client, item)
        val response = request.get

        response.onFailure {

          case e: Throwable => println(s"error on downloading content from ${request.url}: ${e.getMessage}")
        }

        response.map { data =>

          progress.step()
          process(item, data)
        }
      }
    }

    progress.start()
    val response = Await.result(processDownloads.runWith(Sink.seq), Duration.Inf)
    progress.stop()
    wsClient.close()

    response
  }

}
