package scaladex.data
package central

import scala.collection.immutable.Seq
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling._
import akka.stream.scaladsl._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.joda.time.DateTime
import org.json4s._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scaladex.data.maven.PomsReader
import scaladex.data.meta.ArtifactMetaExtractor
import scaladex.infra.CoursierResolver
import scaladex.infra.DataPaths

object CentralMissing {
  // q = g:"com.47deg" AND a:"sbt-microsites"
  // core = gav
  private[CentralMissing] case class SearchBody(response: SearchResponse) {
    def toDocs: List[SearchDoc] = response.docs
  }
  private[CentralMissing] case class SearchResponse(docs: List[SearchDoc])
  private[CentralMissing] case class SearchDoc(
      g: String,
      a: String,
      v: String,
      timestamp: String
  )
  private[CentralMissing] case class SearchRequest(
      groupId: String
  )

  private[CentralMissing] case class DownloadRequest(
      groupId: String,
      artifactId: String,
      version: String,
      created: String
  ) {
    def path: String = {
      val groupIdPath = groupId.replace(".", "/")
      s"/$groupIdPath/$artifactId/$version/$artifactId-$version.pom"
    }
  }

  case class PomContent(content: String, artifact: DownloadRequest)
}

object TimestampSerializer
    extends CustomSerializer[DateTime](format =>
      (
        {
          case JInt(timestamp) =>
            new DateTime(timestamp.toLong)
        },
        {
          case dateTime: DateTime =>
            JInt(dateTime.getMillis)
        }
      )
    )

class CentralMissing(paths: DataPaths)(implicit val system: ActorSystem) {
  import CentralMissing._

  private implicit val formats: Formats =
    DefaultFormats ++ Seq(TimestampSerializer)
  private implicit val serialization: org.json4s.native.Serialization.type =
    native.Serialization

  val log: Logger = LoggerFactory.getLogger(getClass)

  import system.dispatcher

  private val mavenSearchConnectionPool: Flow[
    (HttpRequest, SearchRequest),
    (Try[HttpResponse], SearchRequest),
    Http.HostConnectionPool
  ] =
    Http()
      .cachedHostConnectionPoolHttps[SearchRequest]("search.maven.org")
      .throttle(
        elements = 100,
        per = 1.minute
      )

  private def toHttp(request: SearchRequest): HttpRequest =
    HttpRequest(
      uri = Uri("/solrsearch/select").withQuery(
        Query(
          "q" -> s"""g:"${request.groupId}"""",
          "rows" -> "20000",
          "core" -> "gav"
        )
      ),
      headers = List(Accept(MediaTypes.`application/json`))
    )

  private def parseJson(response: Try[HttpResponse], request: SearchRequest): Future[(List[SearchDoc], SearchRequest)] =
    response match {
      case Success(HttpResponse(StatusCodes.OK, _, entity, _)) =>
        Unmarshal(entity).to[SearchBody].map(body => (body.toDocs, request))
      case Success(res) =>
        log.error(s"Unexpected status code ${res.status} for $request")
        Future.successful((List.empty, request))
      case Failure(e) =>
        Future.failed(new Exception(s"Failed to fetch $request", e))
    }

  private val mavenDownloadConnectionPool
      : Flow[(HttpRequest, DownloadRequest), (Try[HttpResponse], DownloadRequest), Http.HostConnectionPool] =
    Http()
      .cachedHostConnectionPoolHttps[DownloadRequest]("repo1.maven.org")
      .throttle(
        elements = 300,
        per = 1.minute
      )

  private def toHttp(dr: DownloadRequest): HttpRequest =
    HttpRequest(
      uri = "/maven2" + dr.path,
      headers = List(Accept(MediaTypes.`application/xml`))
    )

  private val unmarshal =
    Unmarshaller.stringUnmarshaller.forContentTypes(MediaTypes.`text/xml`)

  private def readContent(response: Try[HttpResponse], request: DownloadRequest): Future[Option[PomContent]] =
    response match {
      case Success(HttpResponse(StatusCodes.OK, _, entity, _)) =>
        unmarshal(entity).map(pom => Some((PomContent(pom, request))))
      case Success(res) =>
        log.error(s"Unexpected status code ${res.status} for $request")
        Future.successful(None)
      case Failure(cause) =>
        Future.failed(new Exception(s"Failed to download $request", cause))
    }

  private def downloadRequest(doc: SearchDoc): DownloadRequest = {
    val SearchDoc(groupId, artifactId, version, created) = doc
    log.info(
      s"Downloading $groupId % $artifactId % $version created on $created"
    )
    DownloadRequest(groupId, artifactId, version, created)
  }

  def savePomsAndMeta(pom: PomContent): Unit =
    ???

  // data/run central /home/gui/scaladex/scaladex-contrib /home/gui/scaladex/scaladex-index /home/gui/scaladex/scaladex-credentials
  def run(): Unit = {
    val metaExtractor = new ArtifactMetaExtractor(paths)
    val pomsReader = new PomsReader(new CoursierResolver)
    val allGroups: Set[String] = ???

    val artifactsDownloads = allGroups.toList.map(SearchRequest(_))

    log.info(s"Updating ${artifactsDownloads.size} organization")

    val missingArtifacts =
      Source(artifactsDownloads)
        .map(ar => (toHttp(ar), ar))
        .via(mavenSearchConnectionPool)
        .mapAsync(8) {
          case (response, request) =>
            parseJson(response, request)
        }
        .mapConcat {
          case (docs, request) =>
            val scala3Artifacts =
              docs.filter(doc => doc.a.endsWith("_3")) // && doc.timestamp.getMillis > 1620597600000L)
            log.info(
              s"Found ${docs.size} total artifacts and ${scala3Artifacts.size} Scala 3 artifacts in ${request.groupId}"
            )
            scala3Artifacts
        }

    val downloadedPoms =
      missingArtifacts
        .map(downloadRequest)
        .map(req => (toHttp(req), req))
        .via(mavenDownloadConnectionPool)
        .mapAsync(8) {
          case (response, request) =>
            readContent(response, request)
        }
        .mapConcat(_.toList)

    val savedPoms = downloadedPoms.runForeach(savePomsAndMeta)

    Await.result(savedPoms, Duration.Inf)
  }
}
