package ch.epfl.scala.index
package data
package bintray

import me.tongfei.progressbar._

import model.Descending

import com.github.nscala_time.time.Imports._

import akka.http.scaladsl.model.{DateTime => _, _}
import Uri.Query
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

  def run() = {
    // todo: config
    val scalaVersion = "2.11"

    val bintrayHttpFlow = Http().cachedHostConnectionPoolHttps[HttpRequest]("bintray.com")
    val progress = new ProgressBar("List POMs", 0)

    val queried = BintrayMeta.sortedByCreated(bintrayCheckpoint)

    val mostRecentQueriedDate = queried.headOption.map(_.created)
    println(s"mostRecentQueriedDate: $mostRecentQueriedDate")

    val query = discover(scalaVersion, createdAfter = mostRecentQueriedDate) _

    // Find the pom total count
    val search0 = {
      val totalHeader = "X-RangeLimit-Total"
      Http().singleRequest(query(0)).map(
        _.headers.find(_.name == totalHeader).map(_.value.toInt).getOrElse(0)
      )
    }
    
    val perRequest = 50
    val searchRequests =
      Source.fromFuture(search0).flatMapConcat{totalPoms =>
        progress.start()
        progress.maxHint(totalPoms)
        
        val requestCount = Math.floor(totalPoms.toDouble / perRequest.toDouble).toInt
        Source((0 to requestCount).map(i => 
          cachedWithoutAuthorization(withAuthorization(query(i * perRequest)))
        ))
      }

    // https pipeline & json extraction
    val listPoms =
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

    val tempCheckpointPath = bintrayIndexBase.resolve(s"bintray_${DateTime.now}.json")
    
    def listPomsCheckpoint(path: Path) = 
      Flow[BintraySearch]
        .map(_.toJson.compactPrint)
        .map(s => ByteString(s + nl))
        .toMat(FileIO.toPath(path))(Keep.right)
  
    Await.result(listPoms.runWith(listPomsCheckpoint(tempCheckpointPath)), Duration.Inf)

    val newlyQueried = BintrayMeta.sortedByCreated(tempCheckpointPath).toSet
    val merged = (queried.toSet ++ newlyQueried).toList.sortBy(_.created)(Descending)
    val tempMergedCheckpointPath = bintrayIndexBase.resolve(s"bintray_merged_${DateTime.now}.json")

    Await.result(Source(merged).runWith(listPomsCheckpoint(tempMergedCheckpointPath)), Duration.Inf)

    Files.delete(bintrayCheckpoint)  // old
    Files.delete(tempCheckpointPath) // unmerged new
    Files.move(tempMergedCheckpointPath, bintrayCheckpoint)
  }

  private def cachedWithoutAuthorization(request: HttpRequest) =
    (request, request.copy(headers = request.headers.filterNot{ case Authorization(_) => true}))

  private def discover(scalaVersion: String, createdAfter: Option[DateTime])(at: Int) = {
    val query0 = Query(
      "name" -> s"*_$scalaVersion*.pom",
      "start_pos" -> at.toString
    )

    val query = createdAfter.fold(query0)(after => 
      ("created_after", after.toLocalDateTime.toString + "Z") +: query0
    )

    HttpRequest(uri = Uri("https://bintray.com/api/v1/search/file").withQuery(query))
  }
}