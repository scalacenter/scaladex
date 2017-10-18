package ch.epfl.scala.index
package data

import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.release.ScalaTarget
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.model.misc.Sha1

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

import akka.stream.Materializer
import akka.stream.ThrottleMode
import akka.stream.scaladsl._

import akka.stream.{OverflowStrategy, QueueOfferResult}

import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.joda.time.DateTime
import org.json4s._
import org.json4s.native.JsonMethods._

import org.slf4j.LoggerFactory

import scala.collection.immutable.Seq

class CentralMissing(
    paths: DataPaths,
    githubDownload: GithubDownload
)(implicit val materializer: Materializer, val system: ActorSystem) {

  import system.dispatcher

  // q=sbt-microsites
  private object Latest {
    case class Body(response: Response)
    case class Response(docs: List[Doc])
    case class Doc(
        g: String,
        a: String,
        latestVersion: String,
        timestamp: DateTime
    )
  }

  // q = g:"com.47deg" AND a:"sbt-microsites"
  // core = gav
  case class SearchBody(response: SearchResponse) {
    def toDocs: List[SearchDoc] = response.docs
  }
  case class SearchResponse(docs: List[SearchDoc])
  case class SearchDoc(
      g: String,
      a: String,
      v: String,
      timestamp: DateTime
  )
  private case class ArtifactRequest(
      groupId: String,
      artifactId: String
  )

  private case class DownloadRequest(
      groupId: String,
      artifactId: String,
      version: String
  )

  private case class PomContent(content: String)

  private implicit val formats = DefaultFormats ++ Seq(DateTimeSerializer)
  private implicit val serialization = native.Serialization

  private val mavenSearchConnectionPool
    : Flow[(HttpRequest, ArtifactRequest),
           (Try[HttpResponse], ArtifactRequest),
           Http.HostConnectionPool] =
    Http()
      .cachedHostConnectionPoolHttps[ArtifactRequest]("search.maven.org")
      .throttle(
        elements = 10,
        per = 1.minute,
        maximumBurst = 10,
        mode = ThrottleMode.Shaping
      )

  private def request(gaRequest: ArtifactRequest): HttpRequest = {
    import gaRequest._

    HttpRequest(
      uri = Uri("/solrsearch/select").withQuery(
        Query(
          "q" -> s"""g:"$groupId" AND a:"$artifactId" """,
          "core" -> "gav"
        )
      ),
      headers = List(Accept(MediaTypes.`application/json`))
    )
  }

  private val parseJson
    : Flow[(Try[HttpResponse], ArtifactRequest),
           Either[String, (List[SearchDoc], ArtifactRequest)],
           akka.NotUsed] = {
    Flow[(Try[HttpResponse], ArtifactRequest)]
      .mapAsyncUnordered(parallelism = 100) {
        case (Success(res @ HttpResponse(StatusCodes.OK, _, entity, _)), ar) => {
          Unmarshal(entity).to[SearchBody].map(gav => Right((gav.toDocs, ar)))
        }
        case (Success(x), ar) =>
          Future.successful(Left(s"Unexpected status code ${x.status} for $ar"))
        case (Failure(e), ar) =>
          Future.failed(new Exception(s"Failed to fetch $ar", e))
      }
  }

  private val mavenDownloadConnectionPool
    : Flow[(HttpRequest, DownloadRequest),
           (Try[HttpResponse], DownloadRequest),
           Http.HostConnectionPool] =
    Http()
      .cachedHostConnectionPoolHttps[DownloadRequest]("repo1.maven.org")
      .throttle(
        elements = 10,
        per = 1.minute,
        maximumBurst = 10,
        mode = ThrottleMode.Shaping
      )

  private def request(dr: DownloadRequest): HttpRequest = {
    import dr._
    val path = groupId.replaceAllLiterally(".", "/")

    HttpRequest(
      uri = s"/maven2/$path/$artifactId/$version/$artifactId-$version.pom",
      headers = List(Accept(MediaTypes.`application/xml`))
    )
  }

  private val unmarshal =
    Unmarshaller.stringUnmarshaller.forContentTypes(MediaTypes.`text/xml`)

  private val readContent: Flow[(Try[HttpResponse], DownloadRequest),
                                Either[String, (PomContent, DownloadRequest)],
                                akka.NotUsed] = {
    Flow[(Try[HttpResponse], DownloadRequest)]
      .mapAsyncUnordered(parallelism = 100) {
        case (Success(res @ HttpResponse(StatusCodes.OK, _, entity, _)), dr) => {

          unmarshal(entity).map(pom => Right((PomContent(pom), dr)))
        }
        case (Success(x), dr) =>
          Future.successful(Left(s"Unexpected status code ${x.status} for $dr"))
        case (Failure(e), dr) =>
          Future.failed(new Exception(s"Failed to fetch $dr", e))
      }
  }

  def run(): Unit = {
    val projectConverter = new ProjectConvert(paths, githubDownload)
    val releases: Set[Release] = projectConverter(
      PomsReader.loadAll(paths).collect {
        case Success(pomAndMeta) => pomAndMeta
      }
    ).map { case (project, releases) => releases }.flatten.toSet

    val scala213 = SemanticVersion("2.13.0-M2").get
    val scala212 = SemanticVersion("2.12").get
    val scala211 = SemanticVersion("2.11").get
    val scala210 = SemanticVersion("2.10").get

    val sbt013 = SemanticVersion("0.13").get
    val sbt10 = SemanticVersion("1.0").get

    val scalaJs06 = SemanticVersion("0.6").get

    val native03 = SemanticVersion("0.3").get

    val allTargets = List(
      ScalaTarget.scala(scala213),
      ScalaTarget.scala(scala212),
      ScalaTarget.scala(scala211),
      ScalaTarget.scala(scala210),
      ScalaTarget.sbt(scala210, sbt013),
      ScalaTarget.sbt(scala210, sbt10),
      ScalaTarget.scalaJs(scala213, scalaJs06),
      ScalaTarget.scalaJs(scala212, scalaJs06),
      ScalaTarget.scalaJs(scala211, scalaJs06),
      ScalaTarget.scalaJs(scala210, scalaJs06),
      ScalaTarget.scalaNative(scala211, native03)
    )

    val releasesDownloads = releases
      .flatMap(
        release =>
          allTargets.map(
            target =>
              ArtifactRequest(release.maven.groupId,
                              release.reference.artifact + target.encode)
        )
      )
      .toList

    val log = LoggerFactory.getLogger(getClass)

    val progress = ProgressBar("Listing", releasesDownloads.size, log)
    progress.start()

    val listArtifactVersions =
      Source(releasesDownloads)
        .map(ar => (request(ar), ar))
        .via(mavenSearchConnectionPool)
        .via(parseJson)
        .alsoTo(Sink.foreach(_ => progress.step()))
        .runWith(Sink.seq)

    val artifactVersions
      : Seq[Either[String, (List[SearchDoc], ArtifactRequest)]] =
      Await.result(listArtifactVersions, 100.minutes)

    progress.stop()

    val toDownload: Seq[DownloadRequest] =
      artifactVersions.collect {
        case Right((docs, _)) => {
          docs.map {
            case SearchDoc(groupId, artifactId, version, created) => {
              DownloadRequest(groupId, artifactId, version)
            }
          }
        }
      }.flatten

    // Meta.append(paths, Meta(data.hash, data.path, data.created), repository)

    val downloadPoms =
      Source(toDownload)
        .map(dr => (request(dr), dr))
        .via(mavenDownloadConnectionPool)
        .via(readContent)
        .runWith(Sink.seq)

    val result: Seq[Either[String, (PomContent, DownloadRequest)]] =
      Await.result(downloadPoms, 100.minutes)

    println(result)
    // get all artifacts from maven central
    // group by latest version => g, a
    // requestGav(g, a)
    //

  }

}
