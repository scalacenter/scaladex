package ch.epfl.scala.index
package data
package download

import me.tongfei.progressbar.ProgressBar
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import play.api.libs.ws.{WSClient, WSConfigParser, WSRequest, WSResponse}
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient, AhcWSClientConfig}
import play.api.{Configuration, Environment, Mode}
import play.api.libs.json._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

trait PlayWsDownloader {

  implicit val system: ActorSystem
  import system.dispatcher
  implicit val materializer: Materializer

  /**
    * Creating a new WS Client - copied from Play website
    *
    * @see https://www.playframework.com/documentation/2.5.x/ScalaWS
    */
  def wsClient = {
    val configuration = Configuration.reference ++ Configuration(
      ConfigFactory.parseString("""
        |plaw.ws.followRedirects = true
      """.stripMargin))

    /* If running in Play, environment should be injected */
    val environment = Environment(new java.io.File("."),
                                  this.getClass.getClassLoader,
                                  Mode.Prod)

    val parser = new WSConfigParser(configuration, environment)
    val config = new AhcWSClientConfig(wsClientConfig = parser.parse())
    val builder = new AhcConfigBuilder(config)
    val ahcBuilder = builder.configure()

    val ahcConfig = ahcBuilder.build()
    new AhcWSClient(ahcConfig)
  }

  /**
    * Creates a fresh client and closes it after the future returned by `f` completes.
    *
    * {{{
    *   managed { client =>
    *     client.url("http://google.com").get()
    *   }
    * }}}
    */
  def managed[A](f: WSClient => Future[A])(
      implicit ec: ExecutionContext): Future[A] =
    Future(wsClient).flatMap(client =>
      f(client).andThen { case _ => client.close() })

  /**
    * Actual download of bunch of documents. Will loop through all and display a status bar in the console output.
    *
    * @param message the message for the loader info
    * @param toDownload the set of downloadable elements
    * @param downloadUrl a function to get the WsRequest for the current element
    * @param process a function to process the response in succes case
    * @param graphqlQuery query sent to Github's GraphQL API
    * @tparam T Input type
    * @tparam R output type
    */
  def download[T, R](
      message: String,
      toDownload: Set[T],
      downloadUrl: (AhcWSClient, T) => WSRequest,
      process: (T, WSResponse) => R,
      parallelism: Int,
      graphqlQuery: (T) => JsObject = null,
      graphqlProcess: (T, WSResponse, AhcWSClient) => R = null
  ): Seq[R] = {

    val client = wsClient
    val progress = new ProgressBar(message, toDownload.size)

    def processDownloads = {

      Source(toDownload).mapAsyncUnordered(parallelism) { item =>
        val request = downloadUrl(client, item)
        val response =
          if (graphqlQuery != null) request.post(graphqlQuery(item))
          else request.get

        response.onFailure {

          case e: Throwable =>
            println(
              s"error on downloading content from ${request.url}: ${e.getMessage}")
        }

        response.map { data =>
          if (toDownload.size > 1) {
            progress.step()
          }
          if (graphqlProcess != null) graphqlProcess(item, data, client)
          else process(item, data)
        }
      }
    }

    if (toDownload.size > 1) {
      progress.start()
    }
    try {
      val response =
        Await.result(processDownloads.runWith(Sink.seq), Duration.Inf)

      if (toDownload.size > 1) {
        progress.stop()
      }
      client.close()
      response
    } catch {
      case e: Exception =>
        println(e.getMessage)
        client.close()
        Seq()
    }
  }

  def retryDownload[T, R](
      item: T,
      downloadUrl: (AhcWSClient, T) => WSRequest,
      graphqlQuery: (T) => JsObject,
      graphqlProcess: (T, WSResponse, AhcWSClient) => R,
      client: AhcWSClient
  ) = {

    val request = downloadUrl(client, item)
    val response =
      if (graphqlQuery != null) request.post(graphqlQuery(item))
      else request.get
    response.onFailure {
      case e: Throwable =>
        println(
          s"error on downloading content from ${request.url}: ${e.getMessage}")
    }
    response.map { data =>
      graphqlProcess(item, data, client)
    }

  }
}
