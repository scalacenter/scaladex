package ch.epfl.scala.index
package data

import ch.epfl.scala.index.model.release.ScalaTarget

import scala.util.{Failure, Success}
import scala.concurrent.{Future, Promise, Await}
import scala.concurrent.duration._
import scala.util.Try

import akka.NotUsed
import akka.actor.ActorSystem

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers._

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling._

import akka.stream.ActorMaterializer
import akka.stream.ThrottleMode
import akka.stream.scaladsl._

import akka.stream.{OverflowStrategy, QueueOfferResult}

import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.joda.time.DateTime
import org.json4s._
import org.json4s.native.JsonMethods._

object TimeStampDateTimeSerializer
    extends CustomSerializer[DateTime](
      format =>
        (
          {
            case JLong(timestamp)  => new DateTime(timestamp)
            case JInt(timestamp)   => new DateTime(timestamp.toLong)
          }, { case time: DateTime => JLong(time.getMillis) }
      )
    )

// q=sbt-microsites
object Latest {
  case class Body(response: Response)
  case class Response(docs: List[Doc])
  case class Doc(
      g: String,
      a: String,
      latestVersion: String
      // timestamp: DateTime
  )
}

// q = g:"com.47deg" AND a:"sbt-microsites"
// core = gav
object Gav {
  case class Body(response: Response)
  case class Response(docs: List[Doc])
  case class Doc(
      g: String,
      a: String,
      v: String,
      timestamp: DateTime
  )
}

object CentralStream {
  implicit val formats = DefaultFormats ++ Seq(TimeStampDateTimeSerializer)
  implicit val serialization = native.Serialization

  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  case class ArtifactRequest(
    groupId: String,
    artifactId: String
  )

  val mavenSearchConnectionPool: Flow[(HttpRequest, ArtifactRequest),(Try[HttpResponse], ArtifactRequest), Http.HostConnectionPool] = 
    Http()
      .cachedHostConnectionPoolHttps[ArtifactRequest]("search.maven.org")
      .throttle(
        elements = 10,
        per = 1.minute, 
        maximumBurst = 10,
        mode = ThrottleMode.Shaping
      )

  def search(gaRequest: ArtifactRequest): HttpRequest = {
    import gaRequest._

    HttpRequest(
      uri = Uri("/solrsearch/select").withQuery(
        Query(
          "q" -> s"""g:"$groupId" AND a:"$artifactId" """,
          "core" -> "gav"
        )
      ),
      headers = collection.immutable.Seq(Accept(MediaTypes.`application/json`))
    )
  }


  def parseJson: Flow[(Try[HttpResponse], ArtifactRequest), Either[String,(Gav.Body, ArtifactRequest)], akka.NotUsed] = {
    Flow[(Try[HttpResponse], ArtifactRequest)].mapAsyncUnordered(parallelism = 100){
      case (Success(res @ HttpResponse(StatusCodes.OK, _, entity, _)), ar) => {
        Unmarshal(entity).to[Gav.Body].map(gav => Right((gav, ar)))
      }
      case (Success(x), ar) => Future.successful(Left(s"Unexpected status code ${x.status} for $ar"))
      case (Failure(e), ar) => Future.failed(new Exception(s"Failed to fetch $ar", e))
    }
  }

  val mavenDownloadConnectionPool: Flow[(HttpRequest, DownloadRequest),(Try[HttpResponse], DownloadRequest), Http.HostConnectionPool] = 
    Http()
      .cachedHostConnectionPoolHttps[DownloadRequest]("repo1.maven.org")
      .throttle(
        elements = 10,
        per = 1.minute, 
        maximumBurst = 10,
        mode = ThrottleMode.Shaping
      )

  case class DownloadRequest(
    groupId: String,
    artifact: String,
    version: String,
    target: ScalaTarget
  )

  case class PomContent(content: String)

  def downloadR(dr: DownloadRequest): HttpRequest = {
    import dr._
    val path = groupId.replaceAllLiterally(".", "/")
    val artifactId = s"$artifact${target.encode}"

    HttpRequest(
      uri = s"/maven2/$path/$artifactId/$version/$artifactId-$version.pom",
      headers = collection.immutable.Seq(Accept(MediaTypes.`application/xml`))
    )
  }

  val unmarshal = Unmarshaller.stringUnmarshaller.forContentTypes(MediaTypes.`text/xml`)

  def readContent: Flow[(Try[HttpResponse], DownloadRequest), Either[String,(PomContent, DownloadRequest)], akka.NotUsed] = {
    Flow[(Try[HttpResponse], DownloadRequest)].mapAsyncUnordered(parallelism = 100){
      case (Success(res @ HttpResponse(StatusCodes.OK, _, entity, _)), dr) => {

        unmarshal(entity).map(pom => Right((PomContent(pom), dr)))
      }
      case (Success(x), dr) => Future.successful(Left(s"Unexpected status code ${x.status} for $dr"))
      case (Failure(e), dr) => Future.failed(new Exception(s"Failed to fetch $dr", e))
    }
  }
}
