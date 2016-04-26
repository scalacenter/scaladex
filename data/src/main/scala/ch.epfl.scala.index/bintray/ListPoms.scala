package ch.epfl.scala.index
package bintray

import me.tongfei.progressbar._

import org.joda.time.DateTime

import akka.http.scaladsl.model._
import Uri._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._

import akka.stream.scaladsl._
import akka.stream.ActorMaterializer
import akka.util.ByteString
import akka.actor.ActorSystem

import scala.concurrent.Future
import scala.util.{Success, Failure}
import scala.concurrent.duration.Duration
import scala.concurrent.Await

import java.nio.file._

class ListPoms(implicit system: ActorSystem, materializer: ActorMaterializer) extends BintrayProtocol with BintrayCredentials {
  import system.dispatcher
  
  private def cachedWithoutAuthorization(request: HttpRequest) =
    (request, request.copy(headers = request.headers.filterNot{ case Authorization(_) => true}))

  private val bintrayHttpFlow = Http().cachedHostConnectionPoolHttps[HttpRequest]("bintray.com")
  
  private val startQuery = "start_pos"
  private def search(start: Int) = {
    // todo: config
    val scalaVersion = "2.11"

    HttpRequest(uri = Uri("https://bintray.com/api/v1/search/file").withQuery(
      Query("name" -> s"*_$scalaVersion*.pom", startQuery -> start.toString)
    ))
  }

  // insannely mutable
  private val progress = new ProgressBar("List POMs", 0)

  // Find the pom total count
  private val search0 = {
    val totalHeader = "X-RangeLimit-Total"
    Http().singleRequest(search(0)).map(
      _.headers.find(_.name == totalHeader).map(_.value.toInt).getOrElse(0)
    )
  }
  
  private val perRequest = 50
  private val searchRequests =
    Source.fromFuture(search0).flatMapConcat{totalPoms =>
      progress.start()
      progress.maxHint(totalPoms)
      
      val requestCount = Math.floor(totalPoms.toDouble / perRequest.toDouble).toInt
      Source((0 to requestCount).map(i => 
        cachedWithoutAuthorization(withAuthorization(search(i * perRequest)))
      ))
    }

  // https pipeline & json extraction
  private val listPoms =
    searchRequests
      .via(bintrayHttpFlow)
      .mapAsync(1){
        case (Success(response), request) => {
          progress.stepBy(perRequest)
          Unmarshal(response).to[List[BintraySearch]].recover {
            case Unmarshaller.UnsupportedContentTypeException(_) => {
              // we will get some 500

              // see https://github.com/akka/akka/issues/20192
              response.entity.dataBytes.runWith(Sink.ignore)
              List()
            }
          }
        }
        case (Failure(e), _) => Future.failed(e)
      }.mapConcat(identity)

  private val checkpointPath = bintrayIndexBase.resolve(s"bintray_${DateTime.now}.json")
  
  private val listPomsCheckpoint = 
    Flow[BintraySearch]
      .map(_.toJson.compactPrint)
      .map(s => ByteString(s + nl))
      .toMat(FileIO.toFile(checkpointPath.toFile))(Keep.right)

  def run() = {
    Await.result(listPoms.runWith(listPomsCheckpoint), Duration.Inf)
    Files.delete(bintrayCheckpoint)
    Files.move(checkpointPath, bintrayCheckpoint)
  }
}