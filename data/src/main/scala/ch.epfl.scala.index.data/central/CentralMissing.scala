package ch.epfl.scala.index
package data

import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.release.{
  SbtPlugin,
  ScalaJs,
  ScalaJvm,
  ScalaNative,
  ScalaTarget
}
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.project.{ArtifactMetaExtractor, ProjectConvert}
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.model.misc.Sha1

import scala.util.{Failure, Success}
import scala.concurrent.{Await, Future, Promise}
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
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.lang.reflect.InvocationTargetException

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
      timestamp: DateTime
  )
  private[CentralMissing] case class ArtifactRequest(
      groupId: String,
      artifactId: String
  )

  private[CentralMissing] case class DownloadRequest(
      groupId: String,
      artifactId: String,
      version: String,
      created: DateTime
  ) {
    def path: String = {
      val groupIdPath = groupId.replaceAllLiterally(".", "/")
      s"/$groupIdPath/$artifactId/$version/$artifactId-$version.pom"
    }
  }

  case class PomContent(content: String)
}

object TimestampSerializer
    extends CustomSerializer[DateTime](
      format =>
        (
          {
            case JInt(timestamp) => new DateTime(timestamp.toLong)
          }, {
            case dateTime: DateTime => JInt(dateTime.getMillis)
          }
      )
    )

class CentralMissing(paths: DataPaths)(implicit val materializer: Materializer,
                                       val system: ActorSystem) {
  import CentralMissing._

  private implicit val formats = DefaultFormats ++ Seq(TimestampSerializer)
  private implicit val serialization = native.Serialization

  val log = LoggerFactory.getLogger(getClass)

  import system.dispatcher

  private val mavenSearchConnectionPool
    : Flow[(HttpRequest, ArtifactRequest),
           (Try[HttpResponse], ArtifactRequest),
           Http.HostConnectionPool] = {

    Http()
      .cachedHostConnectionPoolHttps[ArtifactRequest]("search.maven.org")
      .throttle(
        elements = 100,
        per = 1.minute,
        maximumBurst = 50,
        mode = ThrottleMode.Shaping
      )
  }

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
          Unmarshal(entity)
            .to[SearchBody]
            .map(
              gav => Right((gav.toDocs, ar))
            )
        }
        case (Success(x), ar) =>
          Future.successful(
            Left(s"Unexpected status code ${x.status} for $ar")
          )
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
    HttpRequest(
      uri = "/maven2" + dr.path,
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
          Future.successful(
            Left(s"Unexpected status code ${x.status} for $dr")
          )
        case (Failure(e), dr) =>
          ;
          Future.failed(new Exception(s"Failed to fetch $dr", e))
      }
  }

  private val downloadPoms
    : Flow[Either[String, (List[SearchDoc], ArtifactRequest)],
           Either[String, (PomContent, DownloadRequest)],
           akka.NotUsed] = {

    Flow[Either[String, (List[SearchDoc], ArtifactRequest)]]
      .flatMapConcat {
        case Left(failed) => Source.single(Left(failed))
        case Right((docs, _)) => {
          val toDownload =
            docs.map {
              case SearchDoc(groupId, artifactId, version, created) => {
                DownloadRequest(groupId, artifactId, version, created)
              }
            }

          Source(toDownload)
            .map(dr => (request(dr), dr))
            .via(mavenDownloadConnectionPool)
            .via(readContent)
        }
      }
  }

  def savePomsAndMeta(
      in: Either[String, (PomContent, DownloadRequest)]
  ): Unit = {
    in match {
      case Left(failure) =>
        log.error(failure)

      case Right((pom, request)) => {
        val sha1 = Sha1(pom.content)
        val repository = LocalPomRepository.MavenCentral

        val meta = Meta(sha1, request.path, request.created)

        // write meta
        Meta.append(
          paths,
          meta,
          repository
        )

        // write pom
        val pomPath = paths.poms(repository).resolve(s"$sha1.pom")
        Files.write(pomPath, pom.content.getBytes(StandardCharsets.UTF_8))
      }
    }
  }

  // data/run central /home/gui/scaladex/scaladex-contrib /home/gui/scaladex/scaladex-index /home/gui/scaladex/scaladex-credentials
  def run(): Unit = {
    val artifactMetaExtractor = new ArtifactMetaExtractor(paths)
    val releases: Set[(String, String)] =
      PomsReader(LocalPomRepository.MavenCentral, paths)
        .load()
        .collect {
          case Success((pom, _, _)) =>
            artifactMetaExtractor(pom).flatMap(
              meta =>
                if (meta.scalaTarget.isDefined && !meta.isNonStandard)
                  Some((pom.groupId, meta.artifactName))
                else None
            )
        }
        .flatten
        .toSet

    val scala213 = SemanticVersion("2.13").get
    val scala212 = SemanticVersion("2.12").get
    val scala211 = SemanticVersion("2.11").get
    val scala210 = SemanticVersion("2.10").get
    val sbt013 = SemanticVersion("0.13").get
    val sbt10 = SemanticVersion("1.0").get
    val scalaJs06 = SemanticVersion("0.6").get
    val native03 = SemanticVersion("0.3").get

    val allTargets = List(
      ScalaJvm(scala213),
      ScalaJvm(scala212),
      ScalaJvm(scala211),
      ScalaJvm(scala210),
      SbtPlugin(scala210, sbt013),
      SbtPlugin(scala212, sbt10),
      ScalaJs(scala213, scalaJs06),
      ScalaJs(scala212, scalaJs06),
      ScalaJs(scala211, scalaJs06),
      ScalaJs(scala210, scalaJs06),
      ScalaNative(scala211, native03)
    )

    val releasesDownloads =
      releases.flatMap {
        case (groupId, artifact) =>
          allTargets.map(
            target => ArtifactRequest(groupId, artifact + target.encode)
          )
      }.toList

    val progress = ProgressBar("Listing", releasesDownloads.size, log)
    progress.start()

    val listArtifactVersions =
      Source(releasesDownloads)
        .map(ar => (request(ar), ar))
        .via(mavenSearchConnectionPool)
        .via(parseJson)

    val savePomsAndMetaFlow =
      listArtifactVersions
        .via(downloadPoms)
        .alsoTo(Sink.foreach(_ => progress.step()))
        .runWith(Sink.foreach(savePomsAndMeta))

    Await.result(savePomsAndMetaFlow, Duration.Inf)

    progress.stop()
  }
}
